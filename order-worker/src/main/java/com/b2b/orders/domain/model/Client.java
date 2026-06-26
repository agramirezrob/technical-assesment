package com.b2b.orders.domain.model;

import java.util.Objects;

public record Client(
        String clientId,
        String name,
        String segment,
        String taxRegime,
        String region,
        String channel
) {
    public Client {
        Objects.requireNonNull(clientId, "clientId is required");
        Objects.requireNonNull(name, "name is required");
        Objects.requireNonNull(taxRegime, "taxRegime is required");
    }
}
