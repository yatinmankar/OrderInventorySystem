package com.example.payment.service;

import com.example.payment.domain.Payment;
import com.example.payment.psp.PaymentDeclinedException;
import com.example.payment.psp.PspClient;
import com.example.payment.repo.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.Optional;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    public record Outcome(boolean success, String paymentId, String reason) {}

    private final PaymentRepository paymentRepository;
    private final PspClient pspClient;

    public PaymentService(PaymentRepository paymentRepository, PspClient pspClient) {
        this.paymentRepository = paymentRepository;
        this.pspClient = pspClient;
    }

    /**
     * Idempotent by payment row (keyed by orderId).
     *
     * - success  -> persist COMPLETED, return success
     * - decline  -> persist DECLINED, return failure (saga compensates)
     * - transient outage / open breaker -> exception PROPAGATES (no row persisted), the
     *   listener rethrows, Kafka retries the command and finally routes it to the DLQ.
     *   Because nothing terminal was written, a later replay can still succeed.
     */
    @Transactional
    public Outcome process(String orderId, BigDecimal amount) {
        Optional<Payment> existing = paymentRepository.findById(orderId);
        if (existing.isPresent()) {
            Payment p = existing.get();
            return p.getStatus() == Payment.Status.COMPLETED
                    ? new Outcome(true, p.getPaymentId(), null)
                    : new Outcome(false, null, "already-declined");
        }

        try {
            String paymentId = pspClient.charge(orderId, amount);
            paymentRepository.save(new Payment(orderId, paymentId, amount, Payment.Status.COMPLETED));
            log.info("[{}] payment COMPLETED {}", orderId, paymentId);
            return new Outcome(true, paymentId, null);
        } catch (PaymentDeclinedException declined) {
            paymentRepository.save(new Payment(orderId, null, amount, Payment.Status.DECLINED));
            log.info("[{}] payment DECLINED: {}", orderId, declined.getMessage());
            return new Outcome(false, null, declined.getMessage());
        }
        // PspUnavailableException / CallNotPermittedException are intentionally not caught.
    }

    /** Compensation hook (wired but not on the primary happy/decline paths). Idempotent. */
    @Transactional
    public void refund(String orderId) {
        paymentRepository.findById(orderId).ifPresent(p -> {
            if (p.getStatus() == Payment.Status.COMPLETED) {
                p.setStatus(Payment.Status.REFUNDED);
                log.info("[{}] payment REFUNDED", orderId);
            }
        });
    }
}
