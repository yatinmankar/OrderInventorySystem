package com.example.analytics.web;

import com.example.analytics.domain.OrderStat;
import com.example.analytics.domain.OrderStat.OrderOutcome;
import com.example.analytics.repo.OrderStatRepository;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/analytics/orders")
public class AnalyticsController {

    private final OrderStatRepository orderStatRepository;

    public AnalyticsController(OrderStatRepository orderStatRepository) {
        this.orderStatRepository = orderStatRepository;
    }

    @GetMapping("/confirmed-count")
    public ConfirmedCountResponse confirmedCount() {
        return new ConfirmedCountResponse(orderStatRepository.countByStatus(OrderOutcome.CONFIRMED));
    }

    @GetMapping
    public List<OrderStatResponse> list() {
        return orderStatRepository.findAll().stream()
                .map(s -> new OrderStatResponse(s.getOrderId(), s.getStatus().name(), s.getUpdatedAt()))
                .toList();
    }
}
