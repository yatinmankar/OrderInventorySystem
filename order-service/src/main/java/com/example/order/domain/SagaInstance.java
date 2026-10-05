package com.example.order.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import org.hibernate.envers.Audited;
import org.hibernate.envers.AuditTable;

@Entity
@Table(name = "saga_instance")
@Audited
@AuditTable("saga_instance_aud")
public class SagaInstance {

    public enum SagaState {
        RESERVING_INVENTORY,
        PROCESSING_PAYMENT,
        CONFIRMED,      // terminal, success
        COMPENSATING,   // order already cancelled; waiting for inventory release confirmation
        CANCELLED,      // terminal, compensated
        COMPENSATION_FAILED // terminal, release never confirmed -> manual action needed
    }

    @Id
    @Column(name = "order_id")
    private String orderId;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SagaState state;
    @Column(name = "product_id", nullable = false)
    private String productId;
    @Column(nullable = false)
    private int quantity;
    @Column(nullable = false)
    private BigDecimal amount;
    @Column(name = "payment_id")
    private String paymentId;
    @Column(name = "compensation_attempts", nullable = false)
    private int compensationAttempts;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    protected SagaInstance() {}

    public SagaInstance(String orderId, String productId, int quantity, BigDecimal amount) {
        this.orderId = orderId;
        this.productId = productId;
        this.quantity = quantity;
        this.amount = amount;
        this.state = SagaState.RESERVING_INVENTORY;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    void touch() { this.updatedAt = Instant.now(); }

    public String getOrderId() { return orderId; }
    public SagaState getState() { return state; }
    public void setState(SagaState state) { this.state = state; }
    public String getProductId() { return productId; }
    public int getQuantity() { return quantity; }
    public BigDecimal getAmount() { return amount; }
    public String getPaymentId() { return paymentId; }
    public void setPaymentId(String paymentId) { this.paymentId = paymentId; }
    public Instant getUpdatedAt() { return updatedAt; }
    public int getCompensationAttempts() { return compensationAttempts; }
    public void incrementCompensationAttempts() { this.compensationAttempts++; }
}
