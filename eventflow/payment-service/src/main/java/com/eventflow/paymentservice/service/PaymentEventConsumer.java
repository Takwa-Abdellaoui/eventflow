package com.eventflow.paymentservice.service;

import com.eventflow.paymentservice.event.InventoryReservedEvent;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.DeserializationFeature;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class PaymentEventConsumer {

    private final PaymentService paymentService;
    private final ObjectMapper objectMapper = new ObjectMapper()
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

    @KafkaListener(topics = "${kafka.topics.inventory-reserved}", groupId = "payment-service-group-v4")
    public void handleInventoryReserved(String message) {
        try {
            log.info("Raw message received: {}", message);
            InventoryReservedEvent event = objectMapper.readValue(message, InventoryReservedEvent.class);
            log.info("Parsed event — orderId: {}, success: {}, amount: {}",
                    event.getOrderId(), event.isSuccess(), event.getTotalAmount());
            paymentService.processPayment(event);
        } catch (Exception e) {
            log.error("Deserialization error: {}", e.getMessage(), e);
        }
    }
}