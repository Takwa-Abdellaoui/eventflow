package com.eventflow.inventoryservice.event;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class OrderCreatedEvent {
    private UUID orderId;
    private String customerId;
    private String productId;
    private Integer quantity;
    private BigDecimal totalAmount;
    private LocalDateTime timestamp;
}