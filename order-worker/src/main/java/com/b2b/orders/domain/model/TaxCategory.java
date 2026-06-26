package com.b2b.orders.domain.model;

import java.math.BigDecimal;

public enum TaxCategory {
    GRAVADO(new BigDecimal("0.19")),
    REDUCIDO(new BigDecimal("0.05")),
    EXENTO(BigDecimal.ZERO);

    private final BigDecimal rate;

    TaxCategory(BigDecimal rate) {
        this.rate = rate;
    }

    public BigDecimal rate() {
        return rate;
    }
}
