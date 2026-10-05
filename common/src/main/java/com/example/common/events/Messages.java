package com.example.common.events;

import java.math.BigDecimal;

/**
 * All wire contracts (commands and events) as immutable records, grouped in one file.
 *
 * Every message carries:
 *  - messageId: the idempotency key the *consumer* of this message dedupes on. Producers derive
 *               it DETERMINISTICALLY from (orderId + step), so a redelivered or re-sent message
 *               keeps the same id and is deduped downstream.
 *  - orderId:   also the Kafka partition key, keeping all messages for one order ordered.
 *
 * Reply topics use a single record with a success flag (one message type per topic), which lets
 * the byte[]->type message converter pick the right class from the listener signature.
 */
public final class Messages {
    private Messages() {}

    // ---- Order -> bus (via transaction outbox) ----
    public record OrderCreatedEvent(String messageId, String orderId, String productId,
                                    int quantity, BigDecimal amount) {}

    // ---- Orchestrator -> Inventory ----
    public record ReserveInventoryCommand(String messageId, String orderId, String productId, int quantity) {}
    public record ReleaseInventoryCommand(String messageId, String orderId, String productId, int quantity) {}

    // ---- Inventory -> Orchestrator (reserve.reply) ----
    public record InventoryReply(String messageId, String orderId, boolean success, String reason) {}

    // ---- Inventory -> Orchestrator (release.reply) ----
    public record ReleaseReply(String messageId, String orderId, boolean success) {}

    // ---- Orchestrator -> Payment ----
    public record ProcessPaymentCommand(String messageId, String orderId, BigDecimal amount) {}
    public record RefundPaymentCommand(String messageId, String orderId, String paymentId) {}

    // ---- Payment -> Orchestrator (process.reply) ----
    public record PaymentReply(String messageId, String orderId, boolean success,
                               String paymentId, String reason) {}

    // ---- Orchestrator -> Notification ----
    public record NotificationCommand(String messageId, String orderId, String type, String detail) {
        public static final String CONFIRMATION = "ORDER_CONFIRMED";
        public static final String CANCELLATION = "ORDER_CANCELLED";
    }
}
