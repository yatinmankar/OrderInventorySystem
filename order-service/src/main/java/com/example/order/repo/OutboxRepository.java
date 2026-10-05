package com.example.order.repo;

import com.example.order.domain.OutboxEvent;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEvent, String> {
    List<OutboxEvent> findByPublishedFalseOrderByCreatedAtAsc(Pageable pageable);
}
