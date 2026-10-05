package com.example.inventory.listener;

import com.example.common.Topics;
import com.example.common.aop.TraceOrder;
import com.example.common.events.Messages.*;
import com.example.inventory.service.InventoryService;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
public class InventoryListener {

    private final InventoryService inventoryService;
    private final KafkaTemplate<String, Object> jsonKafkaTemplate;

    public InventoryListener(InventoryService inventoryService, KafkaTemplate<String, Object> jsonKafkaTemplate) {
        this.inventoryService = inventoryService;
        this.jsonKafkaTemplate = jsonKafkaTemplate;
    }

    @KafkaListener(topics = Topics.INVENTORY_RESERVE_CMD, groupId = "inventory-service")
    @TraceOrder
    public void onReserve(ReserveInventoryCommand cmd) {
        InventoryService.Outcome outcome =
                inventoryService.reserve(cmd.orderId(), cmd.productId(), cmd.quantity());
        // Deterministic reply id => orchestrator dedupes on redelivery.
        jsonKafkaTemplate.send(Topics.INVENTORY_RESERVE_REPLY, cmd.orderId(),
                new InventoryReply(cmd.orderId() + ":reserve-reply", cmd.orderId(),
                        outcome.success(), outcome.reason()));
    }

    @KafkaListener(topics = Topics.INVENTORY_RELEASE_CMD, groupId = "inventory-service")
    @TraceOrder
    public void onRelease(ReleaseInventoryCommand cmd) {
        inventoryService.release(cmd.orderId());
        // Always reply (also on redelivery / already released) so the saga can leave COMPENSATING.
        jsonKafkaTemplate.send(Topics.INVENTORY_RELEASE_REPLY, cmd.orderId(),
                new ReleaseReply(cmd.orderId() + ":release-reply", cmd.orderId(), true));
    }
}
