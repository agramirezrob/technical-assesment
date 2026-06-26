package com.b2b.orders.adapters.in.kafka;

import com.b2b.orders.domain.model.OrderItem;
import com.b2b.orders.domain.model.OrderReceived;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

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
                items.stream()
                        .map(item -> new OrderItem(item.productId(), item.quantity(), item.unitPrice()))
                        .toList()
        );
    }

    record Item(String productId, int quantity, BigDecimal unitPrice) {}
}
