package com.example.payment.psp;

/** Transient provider outage: retried by resilience4j, counts toward the circuit breaker. */
public class PspUnavailableException extends RuntimeException {
    public PspUnavailableException(String message) { super(message); }
}
