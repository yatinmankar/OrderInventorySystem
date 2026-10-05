package com.example.order.service;

import com.example.common.Topics;
import com.example.common.aop.TraceOrder;
import com.example.common.events.Messages;
import com.example.order.domain.OrderEntity;
import com.example.order.domain.OutboxEvent;
import com.example.order.repo.OrderRepository;
import com.example.order.repo.OutboxRepository;
import com.example.order.web.CreateOrderRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class OrderService {

    private final OrderRepository orderRepository;
    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    public OrderService(OrderRepository orderRepository, OutboxRepository outboxRepository, ObjectMapper objectMapper) {
        this.orderRepository = orderRepository;
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
    }

    /**
     * The core of the outbox pattern: the order row and the OrderCreated event row are written
     * in ONE local transaction. No Kafka call happens here, so there is no dual-write to lose.
     * The relay drains the outbox to Kafka afterwards.
     */
    @Transactional
    @TraceOrder
    public String createOrder(CreateOrderRequest req) {
        String orderId = UUID.randomUUID().toString();

        orderRepository.save(new OrderEntity(
                orderId, req.productId(), req.quantity(), req.amount(),
                OrderEntity.OrderStatus.PENDING));

        // Deterministic messageId (orderId + step) => safe under at-least-once redelivery.
        var event = new Messages.OrderCreatedEvent(
                orderId + ":created", orderId, req.productId(), req.quantity(), req.amount());

        outboxRepository.save(new OutboxEvent(
                UUID.randomUUID().toString(), orderId, Topics.ORDER_EVENTS, "OrderCreated",
                toJson(event)));

        return orderId;
    }

    private String toJson(Object o) {
        try {
            return objectMapper.writeValueAsString(o);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to serialize outbox payload", e);
        }
    }
}
