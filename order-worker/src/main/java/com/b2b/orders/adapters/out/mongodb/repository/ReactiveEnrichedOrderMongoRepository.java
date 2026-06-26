package com.b2b.orders.adapters.out.mongodb.repository;

import com.b2b.orders.adapters.out.mongodb.document.EnrichedOrderDocument;
import reactor.core.publisher.Mono;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;

public interface ReactiveEnrichedOrderMongoRepository extends ReactiveMongoRepository<EnrichedOrderDocument, String> {
    Mono<Boolean> existsByOrderIdAndStatus(String orderId, String status);

    Mono<EnrichedOrderDocument> findByOrderId(String orderId);
}
