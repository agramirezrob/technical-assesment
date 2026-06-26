package com.b2b.orders.domain.model;

import java.util.Objects;

public record Product(
        String productId,
        String name,
        String sku,
        String category,
        TaxCategory taxCategory,
        String unitOfMeasure
) {
    public Product {
        Objects.requireNonNull(productId, "productId is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(sku, "sku is required");
        Objects.requireNonNull(taxCategory, "taxCategory is required");
    }
}
