package com.b2b.orders.application.port.in;

import com.b2b.orders.domain.model.OrderReceived;
import reactor.core.publisher.Mono;

public interface ProcessOrderUseCase {
    Mono<Void> process(OrderReceived order);
}
