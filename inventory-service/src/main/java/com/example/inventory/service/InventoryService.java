package com.example.inventory.service;

import com.example.inventory.domain.ProductStock;
import com.example.inventory.domain.Reservation;
import com.example.inventory.repo.ProductStockRepository;
import com.example.inventory.repo.ReservationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    public record Outcome(boolean success, String reason) {}

    private final ProductStockRepository stockRepo;
    private final ReservationRepository reservationRepo;

    public InventoryService(ProductStockRepository stockRepo, ReservationRepository reservationRepo) {
        this.stockRepo = stockRepo;
        this.reservationRepo = reservationRepo;
    }

    /** Idempotent: the reservation row (keyed by orderId) is the dedupe key. */
    @Transactional
    public Outcome reserve(String orderId, String productId, int quantity) {
        Optional<Reservation> existing = reservationRepo.findById(orderId);
        if (existing.isPresent()) {
            Reservation r = existing.get();
            return r.getStatus() == Reservation.Status.RESERVED
                    ? new Outcome(true, null)
                    : new Outcome(false, "already-failed");
        }

        Optional<ProductStock> stockOpt = stockRepo.findById(productId);
        if (stockOpt.isEmpty()) {
            reservationRepo.save(new Reservation(orderId, productId, quantity, Reservation.Status.FAILED));
            return new Outcome(false, "unknown-product:" + productId);
        }
        ProductStock stock = stockOpt.get();
        if (stock.getAvailable() < quantity) {
            reservationRepo.save(new Reservation(orderId, productId, quantity, Reservation.Status.FAILED));
            return new Outcome(false, "insufficient-stock:" + stock.getAvailable() + "<" + quantity);
        }

        stock.setAvailable(stock.getAvailable() - quantity);
        reservationRepo.save(new Reservation(orderId, productId, quantity, Reservation.Status.RESERVED));
        log.info("[{}] reserved {} x {} (remaining {})", orderId, quantity, productId, stock.getAvailable());
        return new Outcome(true, null);
    }

    /** Compensation. Idempotent: only a currently-RESERVED reservation is released. */
    @Transactional
    public void release(String orderId) {
        reservationRepo.findById(orderId).ifPresent(r -> {
            if (r.getStatus() == Reservation.Status.RESERVED) {
                stockRepo.findById(r.getProductId()).ifPresent(s ->
                        s.setAvailable(s.getAvailable() + r.getQuantity()));
                r.setStatus(Reservation.Status.RELEASED);
                log.info("[{}] released {} x {}", orderId, r.getQuantity(), r.getProductId());
            }
        });
    }
}
