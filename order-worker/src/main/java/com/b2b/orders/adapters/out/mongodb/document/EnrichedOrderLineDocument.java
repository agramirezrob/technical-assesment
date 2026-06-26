package com.b2b.orders.adapters.out.mongodb.document;

import java.math.BigDecimal;

public record EnrichedOrderLineDocument(
        String productId,
        String name,
        String sku,
        String taxCategory,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal subtotal,
        BigDecimal taxRate,
        BigDecimal taxAmount,
        BigDecimal lineTotal
) {}
