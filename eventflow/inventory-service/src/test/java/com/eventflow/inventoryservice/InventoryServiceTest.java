package com.eventflow.inventoryservice;

import com.eventflow.inventoryservice.entity.Product;
import com.eventflow.inventoryservice.event.InventoryReservedEvent;
import com.eventflow.inventoryservice.event.OrderCreatedEvent;
import com.eventflow.inventoryservice.repository.ProductRepository;
import com.eventflow.inventoryservice.service.InventoryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("InventoryService — Unit Tests")
class InventoryServiceTest {

    @Mock
    private ProductRepository productRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private InventoryService inventoryService;

    private UUID orderId;
    private Product product;
    private OrderCreatedEvent orderEvent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(inventoryService, "inventoryReservedTopic", "inventory.reserved");

        orderId = UUID.randomUUID();

        product = Product.builder()
                .id("PROD-001")
                .name("Laptop Pro 15")
                .stockQuantity(50)
                .price(new BigDecimal("1299.99"))
                .build();

        orderEvent = new OrderCreatedEvent(
                orderId, "CUST-001", "PROD-001", 2,
                new BigDecimal("2599.98"), null
        );

        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    // ──────────────────────────────────────────────────────────────
    // processOrderCreated — succès
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processOrderCreated — should reserve stock and publish success event")
    void processOrderCreated_shouldReserveStock_andPublishSuccess() {
        when(productRepository.findById("PROD-001")).thenReturn(Optional.of(product));
        when(productRepository.decreaseStock("PROD-001", 2)).thenReturn(1);

        inventoryService.processOrderCreated(orderEvent);

        verify(productRepository).decreaseStock("PROD-001", 2);

        ArgumentCaptor<InventoryReservedEvent> captor = ArgumentCaptor.forClass(InventoryReservedEvent.class);
        verify(kafkaTemplate).send(eq("inventory.reserved"), eq(orderId.toString()), captor.capture());

        InventoryReservedEvent published = captor.getValue();
        assertThat(published.isSuccess()).isTrue();
        assertThat(published.getOrderId()).isEqualTo(orderId);
        assertThat(published.getProductId()).isEqualTo("PROD-001");
        assertThat(published.getQuantity()).isEqualTo(2);
        assertThat(published.getReason()).isNull();
    }

    // ──────────────────────────────────────────────────────────────
    // processOrderCreated — produit introuvable
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processOrderCreated — should publish failure when product not found")
    void processOrderCreated_shouldPublishFailure_whenProductNotFound() {
        when(productRepository.findById("PROD-001")).thenReturn(Optional.empty());

        inventoryService.processOrderCreated(orderEvent);

        verify(productRepository, never()).decreaseStock(any(), anyInt());

        ArgumentCaptor<InventoryReservedEvent> captor = ArgumentCaptor.forClass(InventoryReservedEvent.class);
        verify(kafkaTemplate).send(eq("inventory.reserved"), eq(orderId.toString()), captor.capture());

        InventoryReservedEvent published = captor.getValue();
        assertThat(published.isSuccess()).isFalse();
        assertThat(published.getReason()).isEqualTo("Product not found");
    }

    // ──────────────────────────────────────────────────────────────
    // processOrderCreated — stock insuffisant
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processOrderCreated — should publish failure when stock insufficient")
    void processOrderCreated_shouldPublishFailure_whenInsufficientStock() {
        product.setStockQuantity(1); // stock insuffisant pour qty=2
        when(productRepository.findById("PROD-001")).thenReturn(Optional.of(product));

        inventoryService.processOrderCreated(orderEvent);

        verify(productRepository, never()).decreaseStock(any(), anyInt());

        ArgumentCaptor<InventoryReservedEvent> captor = ArgumentCaptor.forClass(InventoryReservedEvent.class);
        verify(kafkaTemplate).send(eq("inventory.reserved"), eq(orderId.toString()), captor.capture());

        InventoryReservedEvent published = captor.getValue();
        assertThat(published.isSuccess()).isFalse();
        assertThat(published.getReason()).contains("Insufficient stock");
    }

    // ──────────────────────────────────────────────────────────────
    // processOrderCreated — race condition (decreaseStock retourne 0)
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processOrderCreated — should publish failure on race condition")
    void processOrderCreated_shouldPublishFailure_onRaceCondition() {
        when(productRepository.findById("PROD-001")).thenReturn(Optional.of(product));
        when(productRepository.decreaseStock("PROD-001", 2)).thenReturn(0); // race condition simulée

        inventoryService.processOrderCreated(orderEvent);

        ArgumentCaptor<InventoryReservedEvent> captor = ArgumentCaptor.forClass(InventoryReservedEvent.class);
        verify(kafkaTemplate).send(eq("inventory.reserved"), eq(orderId.toString()), captor.capture());

        assertThat(captor.getValue().isSuccess()).isFalse();
        assertThat(captor.getValue().getReason()).isEqualTo("Stock update failed");
    }
}
