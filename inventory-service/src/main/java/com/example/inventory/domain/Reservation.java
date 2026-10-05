package com.example.inventory.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "reservation")
public class Reservation {

    public enum Status { RESERVED, FAILED, RELEASED }

    @Id
    @Column(name = "order_id")
    private String orderId;
    @Column(name = "product_id", nullable = false)
    private String productId;
    @Column(nullable = false)
    private int quantity;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Reservation() {}

    public Reservation(String orderId, String productId, int quantity, Status status) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public String getOrderId() { return orderId; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
