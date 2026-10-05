package com.example.order.outbox;

import com.example.order.domain.OutboxEvent;
import com.example.order.repo.OutboxRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Polling publisher. Reads unpublished outbox rows in order and publishes them to Kafka.
 * A row is only marked published AFTER the broker acks, so a crash mid-flush re-sends it
 * (at-least-once) — safe because every consumer is idempotent.
 *
 * Swap this for Debezium CDC in production to remove polling load and cut latency.
 */
@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxRepository outboxRepository;
    private final KafkaTemplate<String, String> stringKafkaTemplate;
    private final int batchSize;

    public OutboxRelay(OutboxRepository outboxRepository,
                       KafkaTemplate<String, String> stringKafkaTemplate,
                       @Value("${outbox.batch-size:100}") int batchSize) {
        this.outboxRepository = outboxRepository;
        this.stringKafkaTemplate = stringKafkaTemplate;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${outbox.poll-delay-ms:1000}")
    @Transactional
    public void flush() {
        List<OutboxEvent> batch =
                outboxRepository.findByPublishedFalseOrderByCreatedAtAsc(PageRequest.of(0, batchSize));
        if (batch.isEmpty()) return;

        for (OutboxEvent e : batch) {
            try {
                // Block on the ack so we never mark a row published before it is durably on the broker.
                stringKafkaTemplate.send(e.getTopic(), e.getAggregateId(), e.getPayload())
                        .get(10, TimeUnit.SECONDS);
                e.markPublished();
                log.info("Outbox -> {} key={} type={}", e.getTopic(), e.getAggregateId(), e.getType());
            } catch (Exception ex) {
                // Leave this and the rest for the next poll; ordering per aggregate is preserved.
                log.warn("Outbox publish failed for {} ({}), will retry next cycle", e.getId(), ex.toString());
                break;
            }
        }
    }
}
