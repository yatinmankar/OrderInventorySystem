package com.example.order.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "outbox")
public class OutboxEvent {

    @Id
    private String id;
    @Column(name = "aggregate_id", nullable = false)
    private String aggregateId;
    @Column(nullable = false)
    private String topic;
    @Column(nullable = false)
    private String type;
    @Column(nullable = false)
    private String payload;
    @Column(nullable = false)
    private boolean published;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OutboxEvent() {}

    public OutboxEvent(String id, String aggregateId, String topic, String type, String payload) {
        this.id = id;
        this.aggregateId = aggregateId;
        this.topic = topic;
        this.type = type;
        this.payload = payload;
        this.published = false;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getAggregateId() { return aggregateId; }
    public String getTopic() { return topic; }
    public String getType() { return type; }
    public String getPayload() { return payload; }
    public boolean isPublished() { return published; }
    public void markPublished() { this.published = true; }
    public Instant getCreatedAt() { return createdAt; }
}
