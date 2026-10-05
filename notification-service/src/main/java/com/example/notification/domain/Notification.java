package com.example.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "notification")
public class Notification {
    @Id
    private String id;
    @Column(name = "order_id", nullable = false)
    private String orderId;
    @Column(nullable = false)
    private String type;
    @Column
    private String detail;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Notification() {}

    public Notification(String id, String orderId, String type, String detail) {
        this.id = id;
        this.orderId = orderId;
        this.type = type;
        this.detail = detail;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getOrderId() { return orderId; }
    public String getType() { return type; }
    public String getDetail() { return detail; }
}
