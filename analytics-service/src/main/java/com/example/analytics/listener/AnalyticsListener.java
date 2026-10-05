package com.example.analytics.listener;

import com.example.analytics.domain.OrderStat;
import com.example.analytics.domain.OrderStat.OrderOutcome;
import com.example.analytics.repo.OrderStatRepository;
import com.example.common.Topics;
import com.example.common.aop.TraceOrder;
import com.example.common.events.Messages.NotificationCommand;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Rides the same notification.cmd topic the notification-service consumes (own consumer
 * group, so it sees every message independently) and keeps a per-order outcome row.
 * Upserting the terminal status is naturally idempotent under redelivery, so no separate
 * dedupe table is needed here.
 */
@Component
public class AnalyticsListener {

    private final OrderStatRepository orderStatRepository;

    public AnalyticsListener(OrderStatRepository orderStatRepository) {
        this.orderStatRepository = orderStatRepository;
    }

    @KafkaListener(topics = Topics.NOTIFICATION_CMD, groupId = "analytics-service")
    @Transactional
    @TraceOrder
    public void onNotification(NotificationCommand cmd) {
        OrderOutcome outcome = NotificationCommand.CONFIRMATION.equals(cmd.type())
                ? OrderOutcome.CONFIRMED
                : OrderOutcome.CANCELLED;

        orderStatRepository.findById(cmd.orderId())
                .ifPresentOrElse(
                        stat -> stat.setStatus(outcome),
                        () -> orderStatRepository.save(new OrderStat(cmd.orderId(), outcome)));
    }
}
