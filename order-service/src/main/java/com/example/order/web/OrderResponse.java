package com.example.order.web;

import io.swagger.v3.oas.annotations.media.Schema;

public record OrderResponse(
        @Schema(description = "Generated order id") String orderId,
        @Schema(description = "Order status", example = "PENDING") String status) {}
