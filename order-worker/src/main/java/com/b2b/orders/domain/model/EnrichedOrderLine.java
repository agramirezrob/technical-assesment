package com.b2b.orders.domain.model;

import java.math.BigDecimal;

public record EnrichedOrderLine(
        String productId,
        String name,
        String sku,
        TaxCategory taxCategory,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal lineTotal
) {}
