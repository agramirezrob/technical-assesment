package com.b2b.orders.application.port.out;

import reactor.core.publisher.Mono;

public interface ProductCatalogPort {
    Mono<ProductData> findById(String productId);

    record ProductData(String productId, String name, String sku, String category, String taxCategory, String unitOfMeasure) {}
}
