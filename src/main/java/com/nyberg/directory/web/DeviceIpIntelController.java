package com.nyberg.directory.web;

import com.nyberg.directory.dto.DirectoryDtos.DeviceIntelBundleResponse;
import com.nyberg.directory.security.AuthSupport;
import com.nyberg.directory.service.DeviceIpIntelService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/orgs/{orgId}/devices/{deviceId}/intel")
@RequiredArgsConstructor
public class DeviceIpIntelController {

    private final DeviceIpIntelService intel;
    private final AuthSupport auth;

    @GetMapping
    public DeviceIntelBundleResponse get(@PathVariable UUID orgId, @PathVariable UUID deviceId) {
        auth.requireOrganizationId(orgId);
        UUID restrictUser;
        if (auth.isServiceToken() || auth.isOrgAdminFromJwt()) {
            if (!auth.isServiceToken()) {
                auth.requireUserId();
            }
            restrictUser = null;
        } else {
            restrictUser = auth.requireUserId();
        }
        return intel.getBundle(orgId, deviceId, restrictUser);
    }
}
