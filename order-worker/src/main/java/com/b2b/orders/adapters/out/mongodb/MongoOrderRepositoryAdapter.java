package com.b2b.orders.adapters.out.mongodb;

import com.b2b.orders.adapters.out.mongodb.document.EnrichedOrderDocument;
import com.b2b.orders.adapters.out.mongodb.repository.ReactiveEnrichedOrderMongoRepository;
import com.b2b.orders.application.port.out.OrderRepositoryPort;
import com.b2b.orders.domain.model.EnrichedOrder;
import com.b2b.orders.domain.model.OrderStatus;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public class MongoOrderRepositoryAdapter implements OrderRepositoryPort {
    private final ReactiveEnrichedOrderMongoRepository repository;
    private final MongoEnrichedOrderMapper mapper = new MongoEnrichedOrderMapper();

    public MongoOrderRepositoryAdapter(ReactiveEnrichedOrderMongoRepository repository) {
        this.repository = repository;
    }

    @Override
    public Mono<Boolean> isProcessed(String orderId) {
        return repository.existsByOrderIdAndStatus(Objects.requireNonNull(orderId, "orderId is required"), OrderStatus.PROCESSED.name());
    }

    @Override
    public Mono<EnrichedOrder> save(EnrichedOrder order) {
        EnrichedOrder safeOrder = Objects.requireNonNull(order, "enriched order is required");
        EnrichedOrderDocument orderDocument = Objects.requireNonNull(mapper.toDocument(safeOrder), "enriched order document is required");
        return repository.save(orderDocument)
                .map(savedDocument -> mapper.toDomain(Objects.requireNonNull(savedDocument, "enriched order document is required")))
                .onErrorResume(DuplicateKeyException.class, error -> repository.findByOrderId(safeOrder.orderId())
                        .map(existingDocument -> mapper.toDomain(Objects.requireNonNull(existingDocument, "enriched order document is required"))));
    }
}
