package com.b2b.orders.domain.model;

import java.math.BigDecimal;

public record OrderItem(String productId, int quantity, BigDecimal unitPrice) {}
