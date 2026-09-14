package com.eventflow.notificationservice.service;

import com.eventflow.notificationservice.event.OrderCreatedEvent;
import com.eventflow.notificationservice.event.PaymentProcessedEvent;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.retry.annotation.Backoff;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class NotificationService {

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "${kafka.topics.order-created}", groupId = "notification-service-group")
    public void handleOrderCreated(OrderCreatedEvent event) {
        // En production : envoyer un email / SMS via SendGrid / Twilio
        log.info("📧 [NOTIFICATION] Order received — customer: {}, orderId: {}, product: {}, qty: {}",
                event.getCustomerId(),
                event.getOrderId(),
                event.getProductId(),
                event.getQuantity());
    }

    @RetryableTopic(
            attempts = "3",
            backoff = @Backoff(delay = 1000, multiplier = 2),
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            dltTopicSuffix = "-dlt"
    )
    @KafkaListener(topics = "${kafka.topics.payment-processed}", groupId = "notification-service-group")
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        if (event.isSuccess()) {
            log.info("✅ [NOTIFICATION] Payment confirmed — customer: {}, orderId: {}, amount: {}, txn: {}",
                    event.getCustomerId(),
                    event.getOrderId(),
                    event.getAmount(),
                    event.getTransactionId());
        } else {
            log.warn("❌ [NOTIFICATION] Payment failed — customer: {}, orderId: {}, reason: {}",
                    event.getCustomerId(),
                    event.getOrderId(),
                    event.getReason());
        }
    }
}
