package com.example.order.saga;

import com.example.order.domain.SagaInstance;
import com.example.order.domain.SagaInstance.SagaState;
import com.example.order.repo.SagaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Recovers sagas stuck in COMPENSATING (release command or its reply lost, inventory down, DLT'd).
 * Re-enqueues the same release command (same messageId, idempotent in inventory) up to
 * max-attempts, then parks the saga in COMPENSATION_FAILED for manual action.
 */
@Component
public class CompensationSweeper {

    private static final Logger log = LoggerFactory.getLogger(CompensationSweeper.class);

    private final SagaRepository sagaRepository;
    private final Orchestrator orchestrator;
    private final long timeoutMs;
    private final int maxAttempts;

    public CompensationSweeper(SagaRepository sagaRepository, Orchestrator orchestrator,
                               @Value("${saga.compensation.timeout-ms:30000}") long timeoutMs,
                               @Value("${saga.compensation.max-attempts:5}") int maxAttempts) {
        this.sagaRepository = sagaRepository;
        this.orchestrator = orchestrator;
        this.timeoutMs = timeoutMs;
        this.maxAttempts = maxAttempts;
    }

    @Scheduled(fixedDelayString = "${saga.compensation.sweep-ms:10000}")
    @Transactional
    public void sweep() {
        List<SagaInstance> stuck = sagaRepository.findByStateAndUpdatedAtBefore(
                SagaState.COMPENSATING, Instant.now().minusMillis(timeoutMs));
        for (SagaInstance s : stuck) {
            if (s.getCompensationAttempts() >= maxAttempts) {
                s.setState(SagaState.COMPENSATION_FAILED);
                log.error("[{}] COMPENSATION_FAILED: inventory release unconfirmed after {} attempts, manual action needed",
                        s.getOrderId(), s.getCompensationAttempts());
            } else {
                s.incrementCompensationAttempts();
                orchestrator.enqueueRelease(s);
                log.warn("[{}] release unconfirmed, re-enqueued (attempt {}/{})",
                        s.getOrderId(), s.getCompensationAttempts(), maxAttempts);
            }
        }
    }
}
