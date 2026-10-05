package com.example.payment.psp;

/** Business decline (e.g. insufficient funds): NOT retried, ignored by the circuit breaker. */
public class PaymentDeclinedException extends RuntimeException {
    public PaymentDeclinedException(String message) { super(message); }
}
