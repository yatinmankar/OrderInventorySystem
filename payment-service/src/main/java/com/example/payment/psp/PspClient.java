package com.example.payment.psp;

import com.example.common.aop.TraceOrder;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Simulated external payment provider, wrapped with retry + circuit breaker.
 *
 * Deterministic demo rules (so every path is reproducible):
 *   - amount >= 5000            -> PspUnavailableException  (transient outage: retried, trips the breaker, then DLQ)
 *   - fractional part == 0.99   -> PaymentDeclinedException (business decline: not retried, saga compensates)
 *   - otherwise                 -> success, returns a payment id
 *
 * Aspect nesting is Retry(CircuitBreaker(call)): each retry attempt passes through the breaker.
 */
@Component
public class PspClient {

    private static final Logger log = LoggerFactory.getLogger(PspClient.class);
    private static final BigDecimal OUTAGE_THRESHOLD = new BigDecimal("5000");
    private static final BigDecimal DECLINE_CENTS = new BigDecimal("0.99");

    @Retry(name = "psp")
    @CircuitBreaker(name = "psp")
    @TraceOrder
    public String charge(String orderId, BigDecimal amount) {
        log.info("[{}] calling PSP for {}", orderId, amount);
        if (amount.compareTo(OUTAGE_THRESHOLD) >= 0) {
            throw new PspUnavailableException("PSP timeout for amount " + amount);
        }
        if (amount.remainder(BigDecimal.ONE).compareTo(DECLINE_CENTS) == 0) {
            throw new PaymentDeclinedException("card-declined");
        }
        return "PAY-" + orderId.substring(0, Math.min(8, orderId.length())).toUpperCase();
    }
}
