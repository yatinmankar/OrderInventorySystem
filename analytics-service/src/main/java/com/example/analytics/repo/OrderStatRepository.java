package com.example.analytics.repo;

import com.example.analytics.domain.OrderStat;
import com.example.analytics.domain.OrderStat.OrderOutcome;
import org.springframework.data.jpa.repository.JpaRepository;

public interface OrderStatRepository extends JpaRepository<OrderStat, String> {
    long countByStatus(OrderOutcome status);
}
