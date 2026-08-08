package com.nyberg.directory.messaging;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
@ConditionalOnProperty(name = "byz.kafka.enabled", havingValue = "true", matchIfMissing = true)
public class TenantLifecycleKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${byz.kafka.topics.directory-tenant:byz.directory.tenant}")
    private String topic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onTenantCreated(TenantCreatedApplicationEvent event) {
        publish(event.getPayload());
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMemberJoined(TenantMemberJoinedApplicationEvent event) {
        publish(event.getPayload());
    }

    private void publish(TenantLifecycleEvent payload) {
        if (payload == null || payload.tenantId() == null) {
            return;
        }
        try {
            String json = objectMapper.writeValueAsString(payload);
            String key = payload.organizationId() != null
                    ? payload.organizationId().toString()
                    : payload.tenantId().toString();
            kafkaTemplate.send(topic, key, json).whenComplete((result, ex) -> {
                if (ex != null) {
                    log.warn("Failed to publish {} eventId={} tenantId={}: {}",
                            payload.type(), payload.eventId(), payload.tenantId(), ex.toString());
                } else {
                    log.info("Published {} eventId={} tenantId={} userId={} topic={}",
                            payload.type(),
                            payload.eventId(),
                            payload.tenantId(),
                            payload.userId(),
                            topic);
                }
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize {} eventId={}: {}", payload.type(), payload.eventId(), e.toString());
        }
    }
}
