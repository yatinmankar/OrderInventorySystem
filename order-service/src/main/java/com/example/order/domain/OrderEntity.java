package com.example.order.domain;

import jakarta.persistence.*;
import org.hibernate.envers.AuditTable;
import org.hibernate.envers.Audited;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "orders")
@Audited
@AuditTable("orders_aud")
public class OrderEntity {

    public enum OrderStatus { PENDING, CONFIRMED, CANCELLED }

    @Id
    private String id;
    @Column(name = "product_id", nullable = false)
    private String productId;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private OrderStatus status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected OrderEntity() {}

    public OrderEntity(String id, String productId, int quantity, BigDecimal amount, OrderStatus status) {
        this.id = id;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public String getId() { return id; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getAmount() { return amount; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public Instant getCreatedAt() { return createdAt; }
}
