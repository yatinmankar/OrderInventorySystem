package com.example.inventory.repo;

import com.example.inventory.domain.ProductStock;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.Optional;

public interface ProductStockRepository extends JpaRepository<ProductStock, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<ProductStock> findById(String productId);
}
