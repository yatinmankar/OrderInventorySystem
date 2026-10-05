package com.example.payment.domain;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "payment")
public class Payment {

    public enum Status { COMPLETED, DECLINED, REFUNDED }

    @Id
    @Column(name = "order_id")
    private String orderId;
    @Column(name = "payment_id")
    private String paymentId;
    @Column(nullable = false)
    private BigDecimal amount;
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status;
    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    protected Payment() {}

    public Payment(String orderId, String paymentId, BigDecimal amount, Status status) {
        this.orderId = orderId;
        this.paymentId = paymentId;
        this.amount = amount;
        this.status = status;
        this.createdAt = Instant.now();
    }

    public String getOrderId() { return orderId; }
    public String getPaymentId() { return paymentId; }
    public BigDecimal getAmount() { return amount; }
    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }
}
