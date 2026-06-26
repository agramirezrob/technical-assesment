package com.b2b.orders.application.port.out;

import reactor.core.publisher.Mono;

public interface OrderRepositoryPort {
    Mono<Boolean> isProcessed(String orderId);
}
