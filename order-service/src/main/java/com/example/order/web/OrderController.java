package com.example.order.web;

import com.example.common.aop.TraceOrder;
import com.example.order.domain.OrderEntity;
import com.example.order.repo.OrderRepository;
import com.example.order.service.OrderService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/orders")
@Tag(name = "Orders", description = "Create orders and check their saga status")
public class OrderController {

    private final OrderService orderService;
    private final OrderRepository orderRepository;

    public OrderController(OrderService orderService, OrderRepository orderRepository) {
        this.orderService = orderService;
        this.orderRepository = orderRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.ACCEPTED)
    @Operation(summary = "Create an order",
            description = "Writes the order and its OrderCreated outbox event in one transaction; the saga runs asynchronously afterwards.")
    @ApiResponse(responseCode = "202", description = "Order accepted, saga started")
    @TraceOrder
    public OrderResponse create(@Valid @RequestBody CreateOrderRequest request) {
        String orderId = orderService.createOrder(request);
        return new OrderResponse(orderId, OrderEntity.OrderStatus.PENDING.name());
    }

    @GetMapping
    @Operation(summary = "Get all orders")
    public List<OrderResponse> getAll() {
        return orderRepository.findAll().stream()
                .map(o -> new OrderResponse(o.getId(), o.getStatus().name()))
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get order status")
    @ApiResponse(responseCode = "200", description = "Order found")
    @ApiResponse(responseCode = "404", description = "Order not found")
    public ResponseEntity<OrderResponse> get(@PathVariable String id) {
        return orderRepository.findById(id)
                .map(o -> ResponseEntity.ok(new OrderResponse(o.getId(), o.getStatus().name())))
                .orElse(ResponseEntity.notFound().build());
    }
}
