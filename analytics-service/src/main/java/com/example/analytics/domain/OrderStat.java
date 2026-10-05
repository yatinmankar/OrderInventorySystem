package com.example.analytics.domain;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One row per order, holding its latest known saga outcome. Fed by the same
 * notification.cmd messages the notification-service sends to customers, so this table is
 * always a (slightly lagging) mirror of order-service's saga_instance terminal state.
 */
@Entity
@Table(name = "order_stats")
public class OrderStat {

    public enum OrderOutcome { CONFIRMED, CANCELLED }

    @Id
    @Column(name = "order_id")
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderOutcome status;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected OrderStat() {}

    public OrderStat(String orderId, OrderOutcome status) {
        this.orderId = orderId;
        this.status = status;
        this.updatedAt = Instant.now();
    }

    public String getOrderId() { return orderId; }
    public OrderOutcome getStatus() { return status; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setStatus(OrderOutcome status) {
        this.status = status;
        this.updatedAt = Instant.now();
    }
}
