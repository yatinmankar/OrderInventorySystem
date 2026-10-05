package com.example.order.dlq;

import org.springframework.data.jpa.repository.JpaRepository;

public interface DeadLetterRepository extends JpaRepository<DeadLetterMessage, Long> {
    boolean existsByDltTopicAndDltPartitionAndDltOffset(String dltTopic, int dltPartition, long dltOffset);
}
