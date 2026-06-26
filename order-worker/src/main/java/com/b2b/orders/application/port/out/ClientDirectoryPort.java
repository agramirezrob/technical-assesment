package com.b2b.orders.application.port.out;

import reactor.core.publisher.Mono;

public interface ClientDirectoryPort {
    Mono<ClientData> findById(String clientId);

    record ClientData(String clientId, String name, String segment, String taxRegime, String region, String channel) {}
}
