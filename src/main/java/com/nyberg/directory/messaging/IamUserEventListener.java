package com.nyberg.directory.messaging;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.nyberg.directory.service.DeviceIpIntelService;
import com.nyberg.directory.service.ProfileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code byz.iam.user}: profile fill on register/auth, IP intel on
 * {@code device.registered} / {@code device.ip_observed}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "byz.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class IamUserEventListener {

    private final ObjectMapper objectMapper;
    private final ProfileService profiles;
    private final DeviceIpIntelService deviceIpIntel;

    @KafkaListener(
            topics = "${byz.kafka.topics.iam-user:byz.iam.user}",
            groupId = "${spring.kafka.consumer.group-id:directory}"
    )
    public void onMessage(String payload) {
        try {
            IamUserLifecycleEvent event = objectMapper.readValue(payload, IamUserLifecycleEvent.class);
            if (event == null || event.type() == null) {
                log.warn("Ignoring empty byz.iam.user payload");
                return;
            }
            boolean profileType = IamUserLifecycleEvent.TYPE_USER_REGISTERED.equals(event.type())
                    || IamUserLifecycleEvent.TYPE_USER_AUTHENTICATED.equals(event.type());
            boolean intelType = IamUserLifecycleEvent.TYPE_DEVICE_REGISTERED.equals(event.type())
                    || IamUserLifecycleEvent.TYPE_DEVICE_IP_OBSERVED.equals(event.type());
            if (!profileType && !intelType) {
                log.debug("Directory ignoring byz.iam.user type={}", event.type());
                return;
            }
            if (event.userId() == null || event.organizationId() == null) {
                log.warn("Ignoring byz.iam.user type={} missing userId/organizationId eventId={}",
                        event.type(), event.eventId());
                return;
            }
            if (profileType) {
                profiles.applyIdentityHint(
                        event.userId(),
                        event.organizationId(),
                        event.email(),
                        event.displayName()
                );
                log.info("Applied identity hint type={} provider={} userId={} orgId={}",
                        event.type(), event.provider(), event.userId(), event.organizationId());
            }
            if (intelType) {
                deviceIpIntel.observe(
                        event.organizationId(),
                        event.userId(),
                        event.deviceId(),
                        event.deviceIp()
                );
            }
        } catch (Exception e) {
            log.error("Failed to process byz.iam.user message: {}", e.toString());
            throw new IllegalStateException("Failed to process byz.iam.user message", e);
        }
    }
}
