package com.eventflow.paymentservice.service;

import com.eventflow.paymentservice.entity.Payment;
import com.eventflow.paymentservice.entity.Payment.PaymentStatus;
import com.eventflow.paymentservice.event.InventoryReservedEvent;
import com.eventflow.paymentservice.event.PaymentProcessedEvent;
import com.eventflow.paymentservice.repository.PaymentRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Random;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PaymentService {

    private final PaymentRepository paymentRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;
    private final Random random = new Random();

    @Value("${kafka.topics.payment-processed}")
    private String paymentProcessedTopic;

    @Value("${payment.success-rate:0.9}")
    private double successRate;

    @Transactional
    public void processPayment(InventoryReservedEvent event) {
        // On ne traite que si l'inventaire a été réservé avec succès
        if (!event.isSuccess()) {
            log.info("Skipping payment for order {} — inventory reservation failed", event.getOrderId());
            return;
        }

        log.info("Processing payment for order {} — amount: {}", event.getOrderId(), event.getTotalAmount());

        // Simulation de paiement (90% succès)
        boolean paymentSuccess = random.nextDouble() < successRate;
        String transactionId = paymentSuccess ? "TXN-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase() : null;
        String failureReason = paymentSuccess ? null : "Payment gateway declined";

        Payment payment = Payment.builder()
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .amount(event.getTotalAmount())
                .status(paymentSuccess ? PaymentStatus.SUCCESS : PaymentStatus.FAILED)
                .transactionId(transactionId)
                .failureReason(failureReason)
                .build();

        paymentRepository.save(payment);

        PaymentProcessedEvent result = PaymentProcessedEvent.builder()
                .orderId(event.getOrderId())
                .customerId(event.getCustomerId())
                .amount(event.getTotalAmount())
                .success(paymentSuccess)
                .transactionId(transactionId)
                .reason(failureReason)
                .build();

        kafkaTemplate.send(paymentProcessedTopic, event.getOrderId().toString(), result)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish PaymentProcessedEvent: {}", ex.getMessage());
                    } else {
                        log.info("PaymentProcessedEvent published — orderId: {}, success: {}, txn: {}",
                                event.getOrderId(), paymentSuccess, transactionId);
                    }
                });
    }
}
