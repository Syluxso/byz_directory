package com.nyberg.directory.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyberg.directory.domain.DeviceIpIntel;
import com.nyberg.directory.intel.MaxMindInsightsClient;
import com.nyberg.directory.repository.DeviceIpIntelRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class DeviceIpIntelServiceTest {

    @Mock DeviceIpIntelRepository repo;
    @Mock MaxMindInsightsClient maxMind;
    @Mock EntityManager entityManager;
    @Mock Query lockQuery;

    DeviceIpIntelService service;

    UUID org = UUID.randomUUID();
    UUID user = UUID.randomUUID();
    UUID device = UUID.randomUUID();

    @BeforeEach
    void setup() {
        service = new DeviceIpIntelService(repo, maxMind, new ObjectMapper(), entityManager);
        when(entityManager.createNativeQuery(anyString())).thenReturn(lockQuery);
        when(lockQuery.setParameter(anyString(), any())).thenReturn(lockQuery);
        when(lockQuery.getSingleResult()).thenReturn(1L);
    }

    @Test
    void recordsUnroutableSkipForPrivateIp() {
        when(repo.findByDeviceIdAndIp(device, "192.168.0.1")).thenReturn(Optional.empty());
        service.observe(org, user, device, "192.168.0.1");
        ArgumentCaptor<DeviceIpIntel> cap = ArgumentCaptor.forClass(DeviceIpIntel.class);
        verify(repo).save(cap.capture());
        assertEquals(DeviceIpIntel.SOURCE_UNROUTABLE, cap.getValue().getSource());
        assertEquals(DeviceIpIntel.STATUS_ERROR, cap.getValue().getStatus());
        verify(maxMind, never()).lookup(anyString());
    }

    @Test
    void bumpsExistingSuccess() {
        DeviceIpIntel row = DeviceIpIntel.builder()
                .organizationId(org)
                .userId(user)
                .deviceId(device)
                .ip("8.8.8.8")
                .status(DeviceIpIntel.STATUS_SUCCESS)
                .source(DeviceIpIntel.SOURCE_MAXMIND_INSIGHTS)
                .firstSeenAt(Instant.now())
                .lastSeenAt(Instant.parse("2020-01-01T00:00:00Z"))
                .build();
        when(repo.findByDeviceIdAndIp(device, "8.8.8.8")).thenReturn(Optional.of(row));

        service.observe(org, user, device, "8.8.8.8");

        verify(maxMind, never()).lookup(anyString());
        verify(repo).save(row);
    }

    @Test
    void copiesCachedPayloadForNewDevice() {
        UUID otherDevice = UUID.randomUUID();
        DeviceIpIntel cache = DeviceIpIntel.builder()
                .organizationId(UUID.randomUUID())
                .userId(UUID.randomUUID())
                .deviceId(otherDevice)
                .ip("8.8.8.8")
                .status(DeviceIpIntel.STATUS_SUCCESS)
                .source(DeviceIpIntel.SOURCE_MAXMIND_INSIGHTS)
                .city("Minneapolis")
                .countryIso("US")
                .firstSeenAt(Instant.now())
                .lastSeenAt(Instant.now())
                .lookedUpAt(Instant.now())
                .build();
        when(repo.findByDeviceIdAndIp(device, "8.8.8.8")).thenReturn(Optional.empty());
        when(repo.findFirstByIpAndStatusOrderByLookedUpAtDesc("8.8.8.8", DeviceIpIntel.STATUS_SUCCESS))
                .thenReturn(Optional.of(cache));

        service.observe(org, user, device, "8.8.8.8");

        verify(maxMind, never()).lookup(anyString());
        ArgumentCaptor<DeviceIpIntel> cap = ArgumentCaptor.forClass(DeviceIpIntel.class);
        verify(repo).save(cap.capture());
        DeviceIpIntel saved = cap.getValue();
        assertEquals(device, saved.getDeviceId());
        assertEquals(org, saved.getOrganizationId());
        assertEquals(user, saved.getUserId());
        assertEquals("Minneapolis", saved.getCity());
        assertEquals(DeviceIpIntel.STATUS_SUCCESS, saved.getStatus());
    }

    @Test
    void looksUpWhenUnknown() {
        when(repo.findByDeviceIdAndIp(device, "8.8.8.8")).thenReturn(Optional.empty());
        when(repo.findFirstByIpAndStatusOrderByLookedUpAtDesc("8.8.8.8", DeviceIpIntel.STATUS_SUCCESS))
                .thenReturn(Optional.empty());
        when(maxMind.configured()).thenReturn(true);
        when(maxMind.lookup("8.8.8.8")).thenReturn("""
                {"country":{"iso_code":"US","names":{"en":"United States"}},"traits":{}}
                """);

        service.observe(org, user, device, "8.8.8.8");

        ArgumentCaptor<DeviceIpIntel> cap = ArgumentCaptor.forClass(DeviceIpIntel.class);
        verify(repo).save(cap.capture());
        assertEquals("US", cap.getValue().getCountryIso());
        assertEquals(DeviceIpIntel.STATUS_SUCCESS, cap.getValue().getStatus());
    }
}
