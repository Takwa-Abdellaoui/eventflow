package com.eventflow.orderservice.service;

import com.eventflow.orderservice.event.InventoryReservedEvent;
import com.eventflow.orderservice.event.PaymentProcessedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class OrderEventConsumer {

    private final OrderService orderService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @KafkaListener(topics = "${kafka.topics.inventory-reserved}", groupId = "order-service-group-v2")
    public void handleInventoryReserved(String message) {
        try {
            InventoryReservedEvent event = objectMapper.readValue(message, InventoryReservedEvent.class);
            log.info("Received InventoryReservedEvent — orderId: {}, success: {}", event.getOrderId(), event.isSuccess());
            orderService.handleInventoryReserved(event);
        } catch (Exception e) {
            log.error("Failed to process InventoryReservedEvent: {}", e.getMessage());
        }
    }

    @KafkaListener(topics = "${kafka.topics.payment-processed}", groupId = "order-service-group-v2")
    public void handlePaymentProcessed(String message) {
        try {
            PaymentProcessedEvent event = objectMapper.readValue(message, PaymentProcessedEvent.class);
            log.info("Received PaymentProcessedEvent — orderId: {}, success: {}", event.getOrderId(), event.isSuccess());
            orderService.handlePaymentProcessed(event);
        } catch (Exception e) {
            log.error("Failed to process PaymentProcessedEvent: {}", e.getMessage());
        }
    }
}