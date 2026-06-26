package com.b2b.orders.adapters.in.kafka;

import java.time.Instant;

record DeadLetterMessage(
        String orderId,
        String originalPayload,
        Instant timestamp,
        String errorClass,
        String cause,
        int attempt
) {}
