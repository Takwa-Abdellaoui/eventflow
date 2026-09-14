package com.eventflow.orderservice;

import com.eventflow.orderservice.dto.OrderRequest;
import com.eventflow.orderservice.dto.OrderResponse;
import com.eventflow.orderservice.entity.Order;
import com.eventflow.orderservice.entity.Order.OrderStatus;
import com.eventflow.orderservice.event.InventoryReservedEvent;
import com.eventflow.orderservice.event.PaymentProcessedEvent;
import com.eventflow.orderservice.exception.OrderNotFoundException;
import com.eventflow.orderservice.repository.OrderRepository;
import com.eventflow.orderservice.service.OrderService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService — Unit Tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private OrderService orderService;

    private OrderRequest validRequest;
    private Order savedOrder;
    private UUID orderId;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(orderService, "orderCreatedTopic", "order.created");

        orderId = UUID.randomUUID();

        validRequest = new OrderRequest();
        validRequest.setCustomerId("CUST-001");
        validRequest.setProductId("PROD-001");
        validRequest.setQuantity(2);
        validRequest.setTotalAmount(new BigDecimal("259.98"));

        savedOrder = Order.builder()
                .id(orderId)
                .customerId("CUST-001")
                .productId("PROD-001")
                .quantity(2)
                .totalAmount(new BigDecimal("259.98"))
                .status(OrderStatus.PENDING)
                .build();

        // Mock KafkaTemplate send — retourne un CompletableFuture complété
        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    // ──────────────────────────────────────────────────────────────
    // createOrder
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("createOrder — should save order and publish Kafka event")
    void createOrder_shouldSaveAndPublishEvent() {
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        OrderResponse response = orderService.createOrder(validRequest);

        assertThat(response).isNotNull();
        assertThat(response.getId()).isEqualTo(orderId);
        assertThat(response.getCustomerId()).isEqualTo("CUST-001");
        assertThat(response.getProductId()).isEqualTo("PROD-001");
        assertThat(response.getQuantity()).isEqualTo(2);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);

        verify(orderRepository, times(1)).save(any(Order.class));
        verify(kafkaTemplate, times(1)).send(eq("order.created"), eq(orderId.toString()), any());
    }

    // ──────────────────────────────────────────────────────────────
    // getOrder
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getOrder — should return order when found")
    void getOrder_shouldReturnOrder_whenFound() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));

        OrderResponse response = orderService.getOrder(orderId);

        assertThat(response.getId()).isEqualTo(orderId);
        assertThat(response.getStatus()).isEqualTo(OrderStatus.PENDING);
    }

    @Test
    @DisplayName("getOrder — should throw OrderNotFoundException when not found")
    void getOrder_shouldThrowException_whenNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrder(unknownId))
                .isInstanceOf(OrderNotFoundException.class)
                .hasMessageContaining(unknownId.toString());
    }

    // ──────────────────────────────────────────────────────────────
    // getAllOrders
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("getAllOrders — should return all orders")
    void getAllOrders_shouldReturnAll() {
        Order order2 = Order.builder()
                .id(UUID.randomUUID())
                .customerId("CUST-002")
                .productId("PROD-002")
                .quantity(1)
                .totalAmount(new BigDecimal("149.99"))
                .status(OrderStatus.CONFIRMED)
                .build();

        when(orderRepository.findAll()).thenReturn(List.of(savedOrder, order2));

        List<OrderResponse> responses = orderService.getAllOrders();

        assertThat(responses).hasSize(2);
        assertThat(responses).extracting(OrderResponse::getCustomerId)
                .containsExactlyInAnyOrder("CUST-001", "CUST-002");
    }

    // ──────────────────────────────────────────────────────────────
    // handleInventoryReserved
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handleInventoryReserved — success should set status INVENTORY_RESERVED")
    void handleInventoryReserved_success_shouldUpdateStatus() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .orderId(orderId)
                .productId("PROD-001")
                .quantity(2)
                .success(true)
                .build();

        orderService.handleInventoryReserved(event);

        verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.INVENTORY_RESERVED));
    }

    @Test
    @DisplayName("handleInventoryReserved — failure should set status CANCELLED")
    void handleInventoryReserved_failure_shouldCancelOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        InventoryReservedEvent event = InventoryReservedEvent.builder()
                .orderId(orderId)
                .productId("PROD-001")
                .quantity(2)
                .success(false)
                .reason("Insufficient stock")
                .build();

        orderService.handleInventoryReserved(event);

        verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
    }

    // ──────────────────────────────────────────────────────────────
    // handlePaymentProcessed
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("handlePaymentProcessed — success should set status CONFIRMED")
    void handlePaymentProcessed_success_shouldConfirmOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .orderId(orderId)
                .customerId("CUST-001")
                .amount(new BigDecimal("259.98"))
                .success(true)
                .transactionId("TXN-ABC123")
                .build();

        orderService.handlePaymentProcessed(event);

        verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CONFIRMED));
    }

    @Test
    @DisplayName("handlePaymentProcessed — failure should set status FAILED")
    void handlePaymentProcessed_failure_shouldFailOrder() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        PaymentProcessedEvent event = PaymentProcessedEvent.builder()
                .orderId(orderId)
                .customerId("CUST-001")
                .amount(new BigDecimal("259.98"))
                .success(false)
                .reason("Payment gateway declined")
                .build();

        orderService.handlePaymentProcessed(event);

        verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.FAILED));
    }

    // ──────────────────────────────────────────────────────────────
    // cancelOrder
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("cancelOrder — should set status CANCELLED")
    void cancelOrder_shouldSetCancelledStatus() {
        when(orderRepository.findById(orderId)).thenReturn(Optional.of(savedOrder));
        when(orderRepository.save(any(Order.class))).thenReturn(savedOrder);

        orderService.cancelOrder(orderId);

        verify(orderRepository).save(argThat(o -> o.getStatus() == OrderStatus.CANCELLED));
    }

    @Test
    @DisplayName("cancelOrder — should throw when order not found")
    void cancelOrder_shouldThrow_whenNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.cancelOrder(unknownId))
                .isInstanceOf(OrderNotFoundException.class);
    }
}
