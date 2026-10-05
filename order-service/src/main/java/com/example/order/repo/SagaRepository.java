package com.example.order.repo;

import com.example.order.domain.SagaInstance;
import com.example.order.domain.SagaInstance.SagaState;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;

public interface SagaRepository extends JpaRepository<SagaInstance, String> {
    List<SagaInstance> findByStateAndUpdatedAtBefore(SagaState state, Instant cutoff);
}
