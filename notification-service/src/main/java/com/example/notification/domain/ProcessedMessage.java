package com.example.notification.domain;

import jakarta.persistence.*;
import java.time.Instant;

@Entity
@Table(name = "processed_messages")
public class ProcessedMessage {
    @Id
    @Column(name = "message_id")
    private String messageId;
    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    protected ProcessedMessage() {}

    public ProcessedMessage(String messageId) {
        this.messageId = messageId;
        this.processedAt = Instant.now();
    }

    public String getMessageId() { return messageId; }
}
