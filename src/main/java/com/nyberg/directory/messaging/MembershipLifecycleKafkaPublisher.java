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
public class MembershipLifecycleKafkaPublisher {

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;

    @Value("${byz.kafka.topics.directory-membership:byz.directory.membership}")
    private String topic;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onMembershipLifecycle(MembershipLifecycleApplicationEvent event) {
        publish(event.getPayload());
    }

    private void publish(MembershipLifecycleEvent payload) {
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
                    log.info("Published {} eventId={} tenantId={} targetUserId={} topic={}",
                            payload.type(),
                            payload.eventId(),
                            payload.tenantId(),
                            payload.targetUserId(),
                            topic);
                }
            });
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize {} eventId={}: {}", payload.type(), payload.eventId(), e.toString());
        }
    }
}
