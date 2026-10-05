package com.example.notification.listener;

import com.example.common.Topics;
import com.example.common.aop.TraceOrder;
import com.example.common.events.Messages.NotificationCommand;
import com.example.notification.domain.Notification;
import com.example.notification.domain.ProcessedMessage;
import com.example.notification.repo.NotificationRepository;
import com.example.notification.repo.ProcessedMessageRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Component
public class NotificationListener {

    private static final Logger log = LoggerFactory.getLogger(NotificationListener.class);

    private final NotificationRepository notificationRepository;
    private final ProcessedMessageRepository processedRepository;

    public NotificationListener(NotificationRepository notificationRepository,
                                ProcessedMessageRepository processedRepository) {
        this.notificationRepository = notificationRepository;
        this.processedRepository = processedRepository;
    }

    @KafkaListener(topics = Topics.NOTIFICATION_CMD, groupId = "notification-service")
    @Transactional
    @TraceOrder
    public void onNotification(NotificationCommand cmd) {
        // Idempotency: a redelivered command carries the same deterministic messageId.
        if (processedRepository.existsById(cmd.messageId())) {
            log.info("[{}] duplicate notification {} ignored", cmd.orderId(), cmd.messageId());
            return;
        }
        processedRepository.save(new ProcessedMessage(cmd.messageId()));
        notificationRepository.save(
                new Notification(UUID.randomUUID().toString(), cmd.orderId(), cmd.type(), cmd.detail()));

        // Stand-in for a real channel (email/SMS/push).
        log.info(">>> NOTIFY customer for order {} : {} ({})", cmd.orderId(), cmd.type(), cmd.detail());
    }
}
