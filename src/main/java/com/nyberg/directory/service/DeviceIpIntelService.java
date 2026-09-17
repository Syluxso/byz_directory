package com.nyberg.directory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyberg.directory.domain.DeviceIpIntel;
import com.nyberg.directory.dto.DirectoryDtos.DeviceIntelBundleResponse;
import com.nyberg.directory.dto.DirectoryDtos.DeviceIpIntelResponse;
import com.nyberg.directory.intel.InsightsMapper;
import com.nyberg.directory.intel.MaxMindInsightsClient;
import com.nyberg.directory.intel.MaxMindPermanentException;
import com.nyberg.directory.intel.MaxMindTransientException;
import com.nyberg.directory.intel.PublicIps;
import com.nyberg.directory.repository.DeviceIpIntelRepository;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeviceIpIntelService {

    private static final Logger log = LoggerFactory.getLogger(DeviceIpIntelService.class);

    private final DeviceIpIntelRepository repo;
    private final MaxMindInsightsClient maxMind;
    private final ObjectMapper objectMapper;
    private final EntityManager entityManager;

    @Value("${byz.maxmind.error-retry-after:PT15M}")
    private Duration errorRetryAfter = Duration.ofMinutes(15);

    @Transactional
    public void observe(UUID organizationId, UUID userId, UUID deviceId, String rawIp) {
        if (organizationId == null || userId == null || deviceId == null) {
            return;
        }
        String ip = PublicIps.normalizeOrNull(rawIp);
        if (ip == null) {
            log.debug("Skipping intel for non-public IP deviceId={}", deviceId);
            return;
        }

        Optional<DeviceIpIntel> existing = repo.findByDeviceIdAndIp(deviceId, ip);
        if (existing.isPresent() && existing.get().isSuccess()) {
            bump(existing.get());
            return;
        }
        if (existing.isPresent() && existing.get().isError() && !retryDue(existing.get())) {
            return;
        }

        lockIp(ip);

        existing = repo.findByDeviceIdAndIp(deviceId, ip);
        if (existing.isPresent() && existing.get().isSuccess()) {
            bump(existing.get());
            return;
        }

        Optional<DeviceIpIntel> cached = repo.findFirstByIpAndStatusOrderByLookedUpAtDesc(
                ip, DeviceIpIntel.STATUS_SUCCESS);
        if (cached.isPresent()) {
            upsertCopy(organizationId, userId, deviceId, ip, cached.get(), existing.orElse(null));
            return;
        }

        fetchAndStore(organizationId, userId, deviceId, ip, existing.orElse(null));
    }

    @Transactional(readOnly = true)
    public DeviceIntelBundleResponse getBundle(UUID organizationId, UUID deviceId, UUID restrictUserId) {
        List<DeviceIpIntel> rows = restrictUserId == null
                ? repo.findByOrganizationIdAndDeviceIdAndStatusOrderByLastSeenAtDesc(
                        organizationId, deviceId, DeviceIpIntel.STATUS_SUCCESS)
                : repo.findByOrganizationIdAndDeviceIdAndUserIdAndStatusOrderByLastSeenAtDesc(
                        organizationId, deviceId, restrictUserId, DeviceIpIntel.STATUS_SUCCESS);
        if (rows.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "No IP intel for device");
        }
        List<DeviceIpIntelResponse> history = rows.stream().map(DeviceIpIntelService::toResponse).toList();
        return new DeviceIntelBundleResponse(history.get(0), history);
    }

    private void fetchAndStore(
            UUID organizationId,
            UUID userId,
            UUID deviceId,
            String ip,
            DeviceIpIntel existing
    ) {
        if (!maxMind.configured()) {
            log.warn("MaxMind Insights skipped (credentials not set) deviceId={} ip={}", deviceId, ip);
            return;
        }
        String body;
        try {
            body = maxMind.lookup(ip);
        } catch (MaxMindTransientException e) {
            throw e;
        } catch (MaxMindPermanentException e) {
            log.warn("MaxMind Insights permanent failure deviceId={} ip={}: {}", deviceId, ip, e.getMessage());
            persistError(organizationId, userId, deviceId, ip, existing);
            return;
        }
        DeviceIpIntel row = existing != null ? existing : newRow(organizationId, userId, deviceId, ip);
        try {
            InsightsMapper.applySuccess(row, body, objectMapper);
        } catch (Exception e) {
            log.warn("MaxMind Insights parse failure deviceId={} ip={}: {}", deviceId, ip, e.toString());
            persistError(organizationId, userId, deviceId, ip, row == existing ? existing : null);
            return;
        }
        Instant now = Instant.now();
        row.setLastSeenAt(now);
        if (row.getFirstSeenAt() == null) {
            row.setFirstSeenAt(now);
        }
        repo.save(row);
        log.info("Stored MaxMind Insights deviceId={} ip={} city={} country={}",
                deviceId, ip, row.getCity(), row.getCountryIso());
    }

    private void persistError(UUID organizationId, UUID userId, UUID deviceId, String ip, DeviceIpIntel existing) {
        Instant now = Instant.now();
        DeviceIpIntel row = existing != null ? existing : newRow(organizationId, userId, deviceId, ip);
        row.setStatus(DeviceIpIntel.STATUS_ERROR);
        row.setSource(DeviceIpIntel.SOURCE_MAXMIND_INSIGHTS);
        row.setLookedUpAt(now);
        row.setLastSeenAt(now);
        repo.save(row);
    }

    private void upsertCopy(
            UUID organizationId,
            UUID userId,
            UUID deviceId,
            String ip,
            DeviceIpIntel cache,
            DeviceIpIntel existing
    ) {
        Instant now = Instant.now();
        DeviceIpIntel row = existing != null ? existing : newRow(organizationId, userId, deviceId, ip);
        row.setOrganizationId(organizationId);
        row.setUserId(userId);
        row.copyIntelFrom(cache);
        row.setLastSeenAt(now);
        if (row.getFirstSeenAt() == null) {
            row.setFirstSeenAt(now);
        }
        repo.save(row);
        log.info("Copied IP intel cache deviceId={} ip={}", deviceId, ip);
    }

    private void bump(DeviceIpIntel row) {
        row.setLastSeenAt(Instant.now());
        repo.save(row);
    }

    private DeviceIpIntel newRow(UUID organizationId, UUID userId, UUID deviceId, String ip) {
        Instant now = Instant.now();
        return DeviceIpIntel.builder()
                .organizationId(organizationId)
                .userId(userId)
                .deviceId(deviceId)
                .ip(ip)
                .source(DeviceIpIntel.SOURCE_MAXMIND_INSIGHTS)
                .status(DeviceIpIntel.STATUS_ERROR)
                .firstSeenAt(now)
                .lastSeenAt(now)
                .build();
    }

    private boolean retryDue(DeviceIpIntel errorRow) {
        Instant looked = errorRow.getLookedUpAt();
        if (looked == null) {
            return true;
        }
        Duration window = errorRetryAfter != null ? errorRetryAfter : Duration.ofMinutes(15);
        return looked.plus(window).isBefore(Instant.now());
    }

    private void lockIp(String ip) {
        entityManager.createNativeQuery("SELECT pg_advisory_xact_lock(hashtextextended(:ip, 0))")
                .setParameter("ip", ip)
                .getSingleResult();
    }

    static DeviceIpIntelResponse toResponse(DeviceIpIntel r) {
        return new DeviceIpIntelResponse(
                r.getId(),
                r.getOrganizationId(),
                r.getUserId(),
                r.getDeviceId(),
                r.getIp(),
                r.getCountry(),
                r.getCountryIso(),
                r.getContinent(),
                r.getContinentCode(),
                r.getRegion(),
                r.getCity(),
                r.getPostalCode(),
                r.getLatitude(),
                r.getLongitude(),
                r.getAccuracyRadiusKm(),
                r.getTimeZone(),
                r.getAsn(),
                r.getAsOrg(),
                r.getIsp(),
                r.getOrganization(),
                r.getConnectionType(),
                r.getUserType(),
                r.getStaticIpScore(),
                r.getUserCount(),
                r.getAnonymous(),
                r.getAnonymousVpn(),
                r.getHosting(),
                r.getPublicProxy(),
                r.getTor(),
                r.getResidentialProxy(),
                r.getMobileCountryCode(),
                r.getMobileNetworkCode(),
                r.getRawJson(),
                r.getSource(),
                r.getStatus(),
                r.getFirstSeenAt(),
                r.getLastSeenAt(),
                r.getLookedUpAt()
        );
    }
}
