package com.example.order.dlq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.header.Header;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;

/**
 * Persists every record that lands on any "<topic>.DLT" topic (all services dead-letter to
 * the same naming scheme) into dead_letter_message, splitting out the business identifiers
 * so the row can be reconciled against the saga. Idempotent on the DLT record's coordinates.
 */
@Component
public class DeadLetterRecorder {

    private static final Logger log = LoggerFactory.getLogger(DeadLetterRecorder.class);
    private static final int MAX_STACKTRACE = 8000;

    private final DeadLetterRepository repository;
    private final ObjectMapper objectMapper;

    public DeadLetterRecorder(DeadLetterRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topicPattern = ".*\\.DLT", groupId = "order-dlt-recorder",
            containerFactory = "dltListenerFactory")
    @Transactional
    public void record(ConsumerRecord<String, byte[]> rec) {
        if (repository.existsByDltTopicAndDltPartitionAndDltOffset(rec.topic(), rec.partition(), rec.offset())) {
            return; // redelivery of an already recorded DLT record
        }

        DeadLetterMessage m = new DeadLetterMessage(rec.topic(), rec.partition(), rec.offset());
        m.setMessageKey(rec.key());
        m.setOriginalTopic(string(rec, KafkaHeaders.DLT_ORIGINAL_TOPIC));
        m.setOriginalPartition(intHeader(rec, KafkaHeaders.DLT_ORIGINAL_PARTITION));
        m.setOriginalOffset(longHeader(rec, KafkaHeaders.DLT_ORIGINAL_OFFSET));
        m.setExceptionClass(string(rec, KafkaHeaders.DLT_EXCEPTION_FQCN));
        m.setExceptionMessage(string(rec, KafkaHeaders.DLT_EXCEPTION_MESSAGE));
        String stack = string(rec, KafkaHeaders.DLT_EXCEPTION_STACKTRACE);
        m.setExceptionStacktrace(stack != null && stack.length() > MAX_STACKTRACE
                ? stack.substring(0, MAX_STACKTRACE) : stack);

        String payload = rec.value() == null ? null : new String(rec.value(), StandardCharsets.UTF_8);
        m.setPayload(payload);
        m.setOrderId(rec.key()); // orderId is the partition key on every saga topic
        extractIds(m, payload);

        repository.save(m);
        log.warn("[{}] dead letter recorded: {} (original {}-{}@{}) cause={}", m.getOrderId(), rec.topic(),
                m.getOriginalTopic(), m.getOriginalPartition(), m.getOriginalOffset(), m.getExceptionClass());
    }

    /** Best effort: a malformed payload must still be stored, just without the extracted ids. */
    private void extractIds(DeadLetterMessage m, String payload) {
        if (payload == null) return;
        try {
            JsonNode n = objectMapper.readTree(payload);
            if (n.hasNonNull("orderId")) m.setOrderId(n.get("orderId").asText());
            if (n.hasNonNull("messageId")) m.setMessageId(n.get("messageId").asText());
        } catch (Exception ignored) {
            // keep raw payload only
        }
    }

    private static String string(ConsumerRecord<?, ?> rec, String name) {
        Header h = rec.headers().lastHeader(name);
        return h == null || h.value() == null ? null : new String(h.value(), StandardCharsets.UTF_8);
    }

    private static Integer intHeader(ConsumerRecord<?, ?> rec, String name) {
        Header h = rec.headers().lastHeader(name);
        return h == null || h.value() == null || h.value().length != Integer.BYTES
                ? null : ByteBuffer.wrap(h.value()).getInt();
    }

    private static Long longHeader(ConsumerRecord<?, ?> rec, String name) {
        Header h = rec.headers().lastHeader(name);
        return h == null || h.value() == null || h.value().length != Long.BYTES
                ? null : ByteBuffer.wrap(h.value()).getLong();
    }
}
