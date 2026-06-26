package com.b2b.orders.adapters.out.mongodb.document;

import java.math.BigDecimal;

public record OrderSummaryDocument(
        BigDecimal subtotal,
        BigDecimal totalTax,
        BigDecimal grandTotal,
        String currency
) {}
