package com.eventflow.inventoryservice.service;

import com.eventflow.inventoryservice.entity.Product;
import com.eventflow.inventoryservice.event.InventoryReservedEvent;
import com.eventflow.inventoryservice.event.OrderCreatedEvent;
import com.eventflow.inventoryservice.repository.ProductRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Slf4j
public class InventoryService {

    private final ProductRepository productRepository;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.inventory-reserved}")
    private String inventoryReservedTopic;

    @Transactional
    public void processOrderCreated(OrderCreatedEvent event) {
        log.info("Processing order {} — product: {}, quantity: {}", event.getOrderId(), event.getProductId(), event.getQuantity());

        Optional<Product> productOpt = productRepository.findById(event.getProductId());

        if (productOpt.isEmpty()) {
            log.warn("Product {} not found for order {}", event.getProductId(), event.getOrderId());
            publishReservationResult(event, false, "Product not found");
            return;
        }

        Product product = productOpt.get();

        if (product.getStockQuantity() < event.getQuantity()) {
            log.warn("Insufficient stock for product {} — available: {}, requested: {}",
                    event.getProductId(), product.getStockQuantity(), event.getQuantity());
            publishReservationResult(event, false,
                    "Insufficient stock — available: " + product.getStockQuantity());
            return;
        }

        // Décrémentation atomique via query JPQL
        int updated = productRepository.decreaseStock(event.getProductId(), event.getQuantity());

        if (updated == 0) {
            log.warn("Stock update failed (race condition?) for product {}", event.getProductId());
            publishReservationResult(event, false, "Stock update failed");
            return;
        }

        log.info("Stock reserved for product {} — quantity: {}", event.getProductId(), event.getQuantity());
        publishReservationResult(event, true, null);
    }

    private void publishReservationResult(OrderCreatedEvent event, boolean success, String reason) {
        InventoryReservedEvent result = InventoryReservedEvent.builder()
                .orderId(event.getOrderId())
                .productId(event.getProductId())
                .quantity(event.getQuantity())
                .success(success)
                .reason(reason)
                .build();

        kafkaTemplate.send(inventoryReservedTopic, event.getOrderId().toString(), result)
                .whenComplete((res, ex) -> {
                    if (ex != null) {
                        log.error("Failed to publish InventoryReservedEvent: {}", ex.getMessage());
                    } else {
                        log.info("InventoryReservedEvent published — orderId: {}, success: {}", event.getOrderId(), success);
                    }
                });
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product getProduct(String id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new RuntimeException("Product not found: " + id));
    }

    public Product addProduct(Product product) {
        return productRepository.save(product);
    }
}
