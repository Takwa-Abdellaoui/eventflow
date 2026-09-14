package com.eventflow.orderservice.service;

import com.eventflow.orderservice.dto.OrderRequest;
import com.eventflow.orderservice.dto.OrderResponse;
import com.eventflow.orderservice.entity.Order;
import com.eventflow.orderservice.entity.Order.OrderStatus;
import com.eventflow.orderservice.event.InventoryReservedEvent;
import com.eventflow.orderservice.event.OrderCreatedEvent;
import com.eventflow.orderservice.event.PaymentProcessedEvent;
import com.eventflow.orderservice.exception.OrderNotFoundException;
import com.eventflow.orderservice.repository.OrderRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

@Service
@RequiredArgsConstructor
@Slf4j
public class OrderService {

    private final OrderRepository orderRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.order-created}")
    private String orderCreatedTopic;

    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        log.info("Creating order for customer: {}, product: {}", request.getCustomerId(), request.getProductId());

        Order order = Order.builder()
                .customerId(request.getCustomerId())
                .productId(request.getProductId())
                .quantity(request.getQuantity())
                .totalAmount(request.getTotalAmount())
                .status(OrderStatus.PENDING)
                .build();

        order = orderRepository.save(order);
        log.info("Order saved with id: {}", order.getId());

        // Publier l'événement Kafka
        OrderCreatedEvent event = OrderCreatedEvent.builder()
                .orderId(order.getId())
                .customerId(order.getCustomerId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .totalAmount(order.getTotalAmount())
                .timestamp(LocalDateTime.now())
                .build();

        publishEvent(orderCreatedTopic, order.getId().toString(), event);

        return mapToResponse(order);
    }

    public OrderResponse getOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
        return mapToResponse(order);
    }

    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(this::mapToResponse)
                .toList();
    }

    public List<OrderResponse> getOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerId(customerId).stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Transactional
    public void handleInventoryReserved(InventoryReservedEvent event) {
        log.info("Handling inventory reserved event for order: {}, success: {}", event.getOrderId(), event.isSuccess());

        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(event.getOrderId()));

        if (event.isSuccess()) {
            order.setStatus(OrderStatus.INVENTORY_RESERVED);
            log.info("Order {} inventory reserved successfully", order.getId());
        } else {
            order.setStatus(OrderStatus.CANCELLED);
            log.warn("Order {} cancelled — inventory not available: {}", order.getId(), event.getReason());
        }

        orderRepository.save(order);
    }

    @Transactional
    public void handlePaymentProcessed(PaymentProcessedEvent event) {
        log.info("Handling payment processed event for order: {}, success: {}", event.getOrderId(), event.isSuccess());

        Order order = orderRepository.findById(event.getOrderId())
                .orElseThrow(() -> new OrderNotFoundException(event.getOrderId()));

        if (event.isSuccess()) {
            order.setStatus(OrderStatus.CONFIRMED);
            log.info("Order {} confirmed — transaction: {}", order.getId(), event.getTransactionId());
        } else {
            order.setStatus(OrderStatus.FAILED);
            log.warn("Order {} payment failed: {}", order.getId(), event.getReason());
        }

        orderRepository.save(order);
    }

    @Transactional
    public OrderResponse cancelOrder(UUID id) {
        Order order = orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));

        order.setStatus(OrderStatus.CANCELLED);
        order = orderRepository.save(order);
        log.info("Order {} cancelled", id);

        return mapToResponse(order);
    }

    private void publishEvent(String topic, String key, Object event) {
        CompletableFuture<SendResult<String, Object>> future = kafkaTemplate.send(topic, key, event);
        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish event to topic {}: {}", topic, ex.getMessage());
            } else {
                log.info("Event published to topic {} partition {} offset {}",
                        topic,
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            }
        });
    }

    private OrderResponse mapToResponse(Order order) {
        return OrderResponse.builder()
                .id(order.getId())
                .customerId(order.getCustomerId())
                .productId(order.getProductId())
                .quantity(order.getQuantity())
                .totalAmount(order.getTotalAmount())
                .status(order.getStatus())
                .createdAt(order.getCreatedAt())
                .updatedAt(order.getUpdatedAt())
                .build();
    }
}
