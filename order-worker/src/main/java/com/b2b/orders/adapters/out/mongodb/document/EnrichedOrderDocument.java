package com.b2b.orders.adapters.out.mongodb.document;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "enriched-orders")
public record EnrichedOrderDocument(
        @Id String id,
        @Indexed(unique = true) String orderId,
        String status,
        ClientDocument client,
        List<EnrichedOrderLineDocument> items,
        OrderSummaryDocument summary,
        Instant processedAt
) {}
