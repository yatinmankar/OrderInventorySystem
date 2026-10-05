package com.example.analytics.web;

import java.time.Instant;

public record OrderStatResponse(String orderId, String status, Instant updatedAt) {}
