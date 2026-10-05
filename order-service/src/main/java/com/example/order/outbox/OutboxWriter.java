package com.example.order.outbox;

import com.example.order.domain.OutboxEvent;
import com.example.order.repo.OutboxRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Writes a message to the outbox. MANDATORY propagation: it must join the caller's transaction,
 * so the business state change and the message are committed (or rolled back) together.
 */
@Component
public class OutboxWriter {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OutboxWriter(OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void write(String topic, String aggregateId, String type, Object payload) {
        try {
            outboxRepository.save(new OutboxEvent(UUID.randomUUID().toString(), aggregateId, topic, type,
                    objectMapper.writeValueAsString(payload)));
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
