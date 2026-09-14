package com.eventflow.paymentservice;

import com.eventflow.paymentservice.entity.Payment;
import com.eventflow.paymentservice.event.InventoryReservedEvent;
import com.eventflow.paymentservice.event.PaymentProcessedEvent;
import com.eventflow.paymentservice.repository.PaymentRepository;
import com.eventflow.paymentservice.service.PaymentService;
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
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("PaymentService — Unit Tests")
class PaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private PaymentService paymentService;

    private UUID orderId;
    private InventoryReservedEvent inventorySuccessEvent;
    private InventoryReservedEvent inventoryFailureEvent;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(paymentService, "paymentProcessedTopic", "payment.processed");
        ReflectionTestUtils.setField(paymentService, "successRate", 1.0); // 100% succès pour les tests déterministes

        orderId = UUID.randomUUID();

        inventorySuccessEvent = new InventoryReservedEvent(
                orderId, "CUST-001", "PROD-001", 2,
                new BigDecimal("2599.98"), true, null
        );

        inventoryFailureEvent = new InventoryReservedEvent(
                orderId, "CUST-001", "PROD-001", 2,
                new BigDecimal("2599.98"), false, "Insufficient stock"
        );

        when(kafkaTemplate.send(any(), any(), any()))
                .thenReturn(CompletableFuture.completedFuture(null));
    }

    // ──────────────────────────────────────────────────────────────
    // processPayment — inventaire réservé avec succès
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processPayment — should save payment and publish success event when inventory reserved")
    void processPayment_shouldSaveAndPublishSuccess_whenInventoryReserved() {
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.processPayment(inventorySuccessEvent);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getOrderId()).isEqualTo(orderId);
        assertThat(saved.getStatus()).isEqualTo(Payment.PaymentStatus.SUCCESS);
        assertThat(saved.getTransactionId()).isNotNull().startsWith("TXN-");

        ArgumentCaptor<PaymentProcessedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(kafkaTemplate).send(eq("payment.processed"), eq(orderId.toString()), eventCaptor.capture());

        PaymentProcessedEvent published = eventCaptor.getValue();
        assertThat(published.isSuccess()).isTrue();
        assertThat(published.getOrderId()).isEqualTo(orderId);
        assertThat(published.getTransactionId()).isNotNull();
    }

    // ──────────────────────────────────────────────────────────────
    // processPayment — inventaire non réservé → skip
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processPayment — should skip when inventory reservation failed")
    void processPayment_shouldSkip_whenInventoryFailed() {
        paymentService.processPayment(inventoryFailureEvent);

        verify(paymentRepository, never()).save(any());
        verify(kafkaTemplate, never()).send(any(), any(), any());
    }

    // ──────────────────────────────────────────────────────────────
    // processPayment — paiement échoue (successRate = 0%)
    // ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("processPayment — should save FAILED payment when gateway declines")
    void processPayment_shouldSaveFailed_whenGatewayDeclines() {
        ReflectionTestUtils.setField(paymentService, "successRate", 0.0); // forcer l'échec
        when(paymentRepository.save(any(Payment.class))).thenAnswer(inv -> inv.getArgument(0));

        paymentService.processPayment(inventorySuccessEvent);

        ArgumentCaptor<Payment> paymentCaptor = ArgumentCaptor.forClass(Payment.class);
        verify(paymentRepository).save(paymentCaptor.capture());

        Payment saved = paymentCaptor.getValue();
        assertThat(saved.getStatus()).isEqualTo(Payment.PaymentStatus.FAILED);
        assertThat(saved.getTransactionId()).isNull();
        assertThat(saved.getFailureReason()).isEqualTo("Payment gateway declined");

        ArgumentCaptor<PaymentProcessedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentProcessedEvent.class);
        verify(kafkaTemplate).send(eq("payment.processed"), eq(orderId.toString()), eventCaptor.capture());

        assertThat(eventCaptor.getValue().isSuccess()).isFalse();
        assertThat(eventCaptor.getValue().getReason()).isEqualTo("Payment gateway declined");
    }
}
