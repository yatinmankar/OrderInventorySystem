package com.example.order.saga;

import com.example.common.Topics;
import com.example.common.aop.TraceOrder;
import com.example.common.events.Messages.*;
import com.example.order.domain.OrderEntity;
import com.example.order.domain.SagaInstance;
import com.example.order.domain.SagaInstance.SagaState;
import com.example.order.outbox.OutboxWriter;
import com.example.order.repo.OrderRepository;
import com.example.order.repo.SagaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Central saga orchestrator (runs inside order-service).
 *
 * Coordinates: reserve inventory -> take payment -> confirm; on failure it compensates in
 * reverse (release inventory) and cancels. Idempotency comes from the persisted saga state:
 * each handler only acts when the saga is in the expected state, and otherwise re-emits the
 * deterministic next message for its current state, so redelivery never double-acts.
 */
@Component
public class Orchestrator {

    private static final Logger log = LoggerFactory.getLogger(Orchestrator.class);

    private final SagaRepository sagaRepository;
    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> jsonKafkaTemplate;
    private final OutboxWriter outboxWriter;

    public Orchestrator(SagaRepository sagaRepository, OrderRepository orderRepository,
                        KafkaTemplate<String, Object> jsonKafkaTemplate, OutboxWriter outboxWriter) {
        this.sagaRepository = sagaRepository;
        this.orderRepository = orderRepository;
        this.jsonKafkaTemplate = jsonKafkaTemplate;
        this.outboxWriter = outboxWriter;
    }

    // Step 1: order created -> start saga, ask inventory to reserve.
    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = "order-orchestrator")
    @Transactional
    @TraceOrder
    public void onOrderCreated(OrderCreatedEvent e) {
        SagaInstance saga = sagaRepository.findById(e.orderId()).orElse(null);
        if (saga == null) {
            saga = new SagaInstance(e.orderId(), e.productId(), e.quantity(), e.amount());
            sagaRepository.save(saga);
            log.info("[{}] saga started -> RESERVING_INVENTORY", e.orderId());
        }
        // (re)send is idempotent downstream thanks to deterministic messageId
        if (saga.getState() == SagaState.RESERVING_INVENTORY) {
            sendReserve(saga);
        }
    }

    // Step 2: inventory reservation result.
    @KafkaListener(topics = Topics.INVENTORY_RESERVE_REPLY, groupId = "order-orchestrator")
    @Transactional
    @TraceOrder
    public void onInventoryReply(InventoryReply reply) {
        SagaInstance saga = sagaRepository.findById(reply.orderId()).orElse(null);
        if (saga == null) return;

        if (reply.success()) {
            if (saga.getState() == SagaState.RESERVING_INVENTORY) {
                saga.setState(SagaState.PROCESSING_PAYMENT);
                log.info("[{}] inventory reserved -> PROCESSING_PAYMENT", saga.getOrderId());
            }
            if (saga.getState() == SagaState.PROCESSING_PAYMENT) {
                sendProcessPayment(saga);
            }
        } else {
            if (saga.getState() == SagaState.RESERVING_INVENTORY) {
                cancel(saga, "Inventory reservation failed: " + reply.reason());
            }
        }
    }

    // Step 3: payment result.
    @KafkaListener(topics = Topics.PAYMENT_PROCESS_REPLY, groupId = "order-orchestrator")
    @Transactional
    @TraceOrder
    public void onPaymentReply(PaymentReply reply) {
        SagaInstance saga = sagaRepository.findById(reply.orderId()).orElse(null);
        if (saga == null) return;

        if (reply.success()) {
            if (saga.getState() == SagaState.PROCESSING_PAYMENT) {
                saga.setPaymentId(reply.paymentId());
                saga.setState(SagaState.CONFIRMED);
                setOrderStatus(saga.getOrderId(), OrderEntity.OrderStatus.CONFIRMED);
                log.info("[{}] payment completed -> CONFIRMED", saga.getOrderId());
            }
            if (saga.getState() == SagaState.CONFIRMED) {
                notify(saga, NotificationCommand.CONFIRMATION, "Order confirmed");
            }
        } else {
            if (saga.getState() == SagaState.PROCESSING_PAYMENT) {
                // Order is cancelled for the customer right away; the inventory release is
                // tracked by the saga (COMPENSATING) and written to the outbox in this same tx.
                saga.setState(SagaState.COMPENSATING);
                setOrderStatus(saga.getOrderId(), OrderEntity.OrderStatus.CANCELLED);
                enqueueRelease(saga);
                log.info("[{}] payment failed -> COMPENSATING (order CANCELLED): {}",
                        saga.getOrderId(), reply.reason());
                notify(saga, NotificationCommand.CANCELLATION, "Payment failed: " + reply.reason());
            }
        }
    }

    // Step 4: inventory release confirmation (compensation finished).
    @KafkaListener(topics = Topics.INVENTORY_RELEASE_REPLY, groupId = "order-orchestrator")
    @Transactional
    @TraceOrder
    public void onReleaseReply(ReleaseReply reply) {
        SagaInstance saga = sagaRepository.findById(reply.orderId()).orElse(null);
        if (saga == null) return;
        if (reply.success() && saga.getState() == SagaState.COMPENSATING) {
            saga.setState(SagaState.CANCELLED);
            log.info("[{}] inventory released -> CANCELLED", saga.getOrderId());
        }
    }

    // ---- transitions / emitters (deterministic messageIds) ----

    private void sendReserve(SagaInstance s) {
        jsonKafkaTemplate.send(Topics.INVENTORY_RESERVE_CMD, s.getOrderId(),
                new ReserveInventoryCommand(s.getOrderId() + ":reserve", s.getOrderId(),
                        s.getProductId(), s.getQuantity()));
    }

    private void sendProcessPayment(SagaInstance s) {
        jsonKafkaTemplate.send(Topics.PAYMENT_PROCESS_CMD, s.getOrderId(),
                new ProcessPaymentCommand(s.getOrderId() + ":payment", s.getOrderId(), s.getAmount()));
    }

    /** Must be called inside a transaction: the command is written to the outbox, not sent directly. */
    public void enqueueRelease(SagaInstance s) {
        outboxWriter.write(Topics.INVENTORY_RELEASE_CMD, s.getOrderId(), "ReleaseInventory",
                new ReleaseInventoryCommand(s.getOrderId() + ":release", s.getOrderId(),
                        s.getProductId(), s.getQuantity()));
        log.info("[{}] compensation -> release inventory (outbox)", s.getOrderId());
    }

    private void cancel(SagaInstance s, String reason) {
        s.setState(SagaState.CANCELLED);
        setOrderStatus(s.getOrderId(), OrderEntity.OrderStatus.CANCELLED);
        log.info("[{}] saga CANCELLED: {}", s.getOrderId(), reason);
        notify(s, NotificationCommand.CANCELLATION, reason);
    }

    private void notify(SagaInstance s, String type, String detail) {
        jsonKafkaTemplate.send(Topics.NOTIFICATION_CMD, s.getOrderId(),
                new NotificationCommand(s.getOrderId() + ":notify", s.getOrderId(), type, detail));
    }

    private void setOrderStatus(String orderId, OrderEntity.OrderStatus status) {
        orderRepository.findById(orderId).ifPresent(o -> o.setStatus(status));
    }
}
