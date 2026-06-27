package com.b2b.orders.adapters.in.kafka;

import com.b2b.orders.domain.model.OrderItem;
import com.b2b.orders.domain.model.OrderReceived;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Objects;

record OrderKafkaMessage(
        String orderId,
        String clientId,
        String channel,
        Instant createdAt,
        List<Item> items
) {
    OrderReceived toDomain() {
        return new OrderReceived(
                orderId,
                clientId,
                channel,
                createdAt,
                requireItems().stream()
                        .map(item -> Objects.requireNonNull(item, "order item is required").toDomain())
                        .toList()
        );
    }

    private List<Item> requireItems() {
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        return items;
    }

    record Item(String productId, int quantity, BigDecimal unitPrice) {
        OrderItem toDomain() {
            return new OrderItem(productId, quantity, Objects.requireNonNull(unitPrice, "unitPrice is required"));
        }
    }
}
