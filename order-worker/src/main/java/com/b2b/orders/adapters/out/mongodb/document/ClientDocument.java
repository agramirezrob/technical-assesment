package com.b2b.orders.adapters.out.mongodb.document;

public record ClientDocument(
        String clientId,
        String name,
        String segment,
        String taxRegime,
        String region,
        String channel
) {}
