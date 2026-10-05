package com.example.order.web;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;

public record CreateOrderRequest(
        @Schema(description = "Product to order", example = "sku-123") @NotBlank String productId,
        @Schema(description = "Quantity to order", example = "2") @Positive int quantity,
        @Schema(description = "Total order amount", example = "49.99") @NotNull @DecimalMin("0.01") BigDecimal amount) {}
