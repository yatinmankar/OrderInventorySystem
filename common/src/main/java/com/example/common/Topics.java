package com.example.common;

/** Central registry of Kafka topic names. One message type per topic. */
public final class Topics {
    private Topics() {}

    public static final String ORDER_EVENTS            = "order.events";            // OrderCreatedEvent
    public static final String INVENTORY_RESERVE_CMD   = "inventory.reserve.cmd";   // ReserveInventoryCommand
    public static final String INVENTORY_RESERVE_REPLY = "inventory.reserve.reply"; // InventoryReserved / InventoryFailed
    public static final String INVENTORY_RELEASE_CMD   = "inventory.release.cmd";   // ReleaseInventoryCommand
    public static final String INVENTORY_RELEASE_REPLY = "inventory.release.reply"; // ReleaseReply
    public static final String PAYMENT_PROCESS_CMD    = "payment.process.cmd";     // ProcessPaymentCommand
    public static final String PAYMENT_PROCESS_REPLY   = "payment.process.reply";   // PaymentCompleted / PaymentFailed
    public static final String PAYMENT_REFUND_CMD      = "payment.refund.cmd";      // RefundPaymentCommand
    public static final String NOTIFICATION_CMD        = "notification.cmd";        // NotificationCommand
}
