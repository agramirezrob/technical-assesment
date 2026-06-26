package com.b2b.orders.application.port.out;

import com.b2b.orders.domain.model.OrderReceived;
import reactor.core.publisher.Mono;

public interface DeadLetterPort {
    Mono<Void> publish(OrderReceived order, Throwable error, int attempt);
}
