package com.b2b.orders.domain.model;

import java.time.Instant;
import java.util.List;
import java.util.Objects;

public record OrderReceived(
        String orderId,
        String clientId,
        String channel,
        Instant createdAt,
        List<OrderItem> items
) {
    public OrderReceived {
        if (orderId == null || orderId.isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        if (clientId == null || clientId.isBlank()) {
            throw new IllegalArgumentException("clientId is required");
        }
        if (channel == null || channel.isBlank()) {
            throw new IllegalArgumentException("channel is required");
        }
        Objects.requireNonNull(createdAt, "createdAt is required");
        if (items == null || items.isEmpty()) {
            throw new IllegalArgumentException("items must not be empty");
        }
        items = List.copyOf(items);
    }
}
