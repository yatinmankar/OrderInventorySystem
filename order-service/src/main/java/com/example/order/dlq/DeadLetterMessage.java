package com.example.order.dlq;

import jakarta.persistence.*;
import java.time.Instant;

/** A record taken from a "<topic>.DLT" topic, kept for reconciliation. */
@Entity
@Table(name = "dead_letter_message")
public class DeadLetterMessage {

    public enum Status { NEW, REPLAYED, RESOLVED, DISCARDED }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "dlt_topic", nullable = false)
    private String dltTopic;
    @Column(name = "dlt_partition", nullable = false)
    private int dltPartition;
    @Column(name = "dlt_offset", nullable = false)
    private long dltOffset;

    @Column(name = "original_topic")
    private String originalTopic;
    @Column(name = "original_partition")
    private Integer originalPartition;
    @Column(name = "original_offset")
    private Long originalOffset;

    @Column(name = "order_id")
    private String orderId;
    @Column(name = "message_id")
    private String messageId;
    @Column(name = "message_key")
    private String messageKey;

    private String payload;

    @Column(name = "exception_class")
    private String exceptionClass;
    @Column(name = "exception_message")
    private String exceptionMessage;
    @Column(name = "exception_stacktrace")
    private String exceptionStacktrace;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.NEW;
    @Column(name = "resolution_note")
    private String resolutionNote;
    @Column(name = "resolved_at")
    private Instant resolvedAt;

    @Column(name = "received_at", nullable = false)
    private Instant receivedAt = Instant.now();

    protected DeadLetterMessage() {}

    public DeadLetterMessage(String dltTopic, int dltPartition, long dltOffset) {
        this.dltTopic = dltTopic;
        this.dltPartition = dltPartition;
        this.dltOffset = dltOffset;
    }

    public Long getId() { return id; }
    public String getDltTopic() { return dltTopic; }
    public int getDltPartition() { return dltPartition; }
    public long getDltOffset() { return dltOffset; }
    public String getOriginalTopic() { return originalTopic; }
    public void setOriginalTopic(String originalTopic) { this.originalTopic = originalTopic; }
    public Integer getOriginalPartition() { return originalPartition; }
    public void setOriginalPartition(Integer originalPartition) { this.originalPartition = originalPartition; }
    public Long getOriginalOffset() { return originalOffset; }
    public void setOriginalOffset(Long originalOffset) { this.originalOffset = originalOffset; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public String getMessageId() { return messageId; }
    public void setMessageId(String messageId) { this.messageId = messageId; }
    public String getMessageKey() { return messageKey; }
    public void setMessageKey(String messageKey) { this.messageKey = messageKey; }
    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }
    public String getExceptionClass() { return exceptionClass; }
    public void setExceptionClass(String exceptionClass) { this.exceptionClass = exceptionClass; }
    public String getExceptionMessage() { return exceptionMessage; }
    public void setExceptionMessage(String exceptionMessage) { this.exceptionMessage = exceptionMessage; }
    public String getExceptionStacktrace() { return exceptionStacktrace; }
    public void setExceptionStacktrace(String exceptionStacktrace) { this.exceptionStacktrace = exceptionStacktrace; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
    public String getResolutionNote() { return resolutionNote; }
    public void setResolutionNote(String resolutionNote) { this.resolutionNote = resolutionNote; }
    public Instant getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(Instant resolvedAt) { this.resolvedAt = resolvedAt; }
    public Instant getReceivedAt() { return receivedAt; }
}
