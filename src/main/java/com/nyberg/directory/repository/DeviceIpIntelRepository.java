package com.nyberg.directory.repository;

import com.nyberg.directory.domain.DeviceIpIntel;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface DeviceIpIntelRepository extends JpaRepository<DeviceIpIntel, UUID> {

    Optional<DeviceIpIntel> findByDeviceIdAndIp(UUID deviceId, String ip);

    Optional<DeviceIpIntel> findFirstByIpAndStatusOrderByLookedUpAtDesc(String ip, String status);

    List<DeviceIpIntel> findByOrganizationIdAndDeviceIdAndStatusOrderByLastSeenAtDesc(
            UUID organizationId, UUID deviceId, String status);

    List<DeviceIpIntel> findByOrganizationIdAndDeviceIdAndUserIdAndStatusOrderByLastSeenAtDesc(
            UUID organizationId, UUID deviceId, UUID userId, String status);
}
