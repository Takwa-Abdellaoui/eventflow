package com.eventflow.inventoryservice.service;

import com.eventflow.inventoryservice.event.OrderCreatedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class InventoryEventConsumer {

    private final InventoryService inventoryService;

    @KafkaListener(topics = "${kafka.topics.order-created}", groupId = "inventory-service-group-v3")
    public void handleOrderCreated(OrderCreatedEvent event) {
        log.info("Received OrderCreatedEvent for order: {}", event.getOrderId());
        inventoryService.processOrderCreated(event);
    }
}