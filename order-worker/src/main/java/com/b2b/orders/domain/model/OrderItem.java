package com.b2b.orders.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

public record OrderItem(String productId, int quantity, BigDecimal unitPrice) {
    public OrderItem {
        if (productId == null || productId.isBlank()) {
            throw new IllegalArgumentException("productId is required");
        }
        if (quantity <= 0) {
            throw new IllegalArgumentException("quantity must be greater than zero");
        }
        Objects.requireNonNull(unitPrice, "unitPrice is required");
        if (unitPrice.signum() < 0) {
            throw new IllegalArgumentException("unitPrice must not be negative");
        }
    }
}
