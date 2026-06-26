package com.b2b.orders.adapters.out.mongodb;

import com.b2b.orders.adapters.out.mongodb.document.ClientDocument;
import com.b2b.orders.adapters.out.mongodb.document.EnrichedOrderDocument;
import com.b2b.orders.adapters.out.mongodb.document.EnrichedOrderLineDocument;
import com.b2b.orders.adapters.out.mongodb.document.OrderSummaryDocument;
import com.b2b.orders.domain.model.Client;
import com.b2b.orders.domain.model.EnrichedOrder;
import com.b2b.orders.domain.model.EnrichedOrderLine;
import com.b2b.orders.domain.model.OrderStatus;
import com.b2b.orders.domain.model.OrderSummary;
import com.b2b.orders.domain.model.TaxCategory;

import java.util.Objects;

final class MongoEnrichedOrderMapper {
    EnrichedOrderDocument toDocument(EnrichedOrder order) {
        Objects.requireNonNull(order, "enriched order is required");
        return new EnrichedOrderDocument(
                null,
                order.orderId(),
                order.status().name(),
                toDocument(order.client()),
                order.items().stream()
                        .map(line -> toDocument(Objects.requireNonNull(line, "enriched order line is required")))
                        .toList(),
                toDocument(order.summary()),
                order.processedAt()
        );
    }

    EnrichedOrder toDomain(EnrichedOrderDocument document) {
        Objects.requireNonNull(document, "enriched order document is required");
        return new EnrichedOrder(
                document.orderId(),
                OrderStatus.valueOf(document.status()),
                toDomain(document.client()),
                document.items().stream()
                        .map(line -> toDomain(Objects.requireNonNull(line, "enriched order line document is required")))
                        .toList(),
                toDomain(document.summary()),
                document.processedAt()
        );
    }

    private ClientDocument toDocument(Client client) {
        Objects.requireNonNull(client, "client is required");
        return new ClientDocument(client.clientId(), client.name(), client.segment(), client.taxRegime(), client.region(), client.channel());
    }

    private Client toDomain(ClientDocument document) {
        Objects.requireNonNull(document, "client document is required");
        return new Client(document.clientId(), document.name(), document.segment(), document.taxRegime(), document.region(), document.channel());
    }

    private EnrichedOrderLineDocument toDocument(EnrichedOrderLine line) {
        Objects.requireNonNull(line, "enriched order line is required");
        return new EnrichedOrderLineDocument(
                line.productId(),
                line.name(),
                line.sku(),
                line.taxCategory().name(),
                line.quantity(),
                line.unitPrice(),
                line.subtotal(),
                line.taxRate(),
                line.taxAmount(),
                line.lineTotal()
        );
    }

    private EnrichedOrderLine toDomain(EnrichedOrderLineDocument document) {
        Objects.requireNonNull(document, "enriched order line document is required");
        return new EnrichedOrderLine(
                document.productId(),
                document.name(),
                document.sku(),
                TaxCategory.valueOf(document.taxCategory()),
                document.quantity(),
                document.unitPrice(),
                document.subtotal(),
                document.taxRate(),
                document.taxAmount(),
                document.lineTotal()
        );
    }

    private OrderSummaryDocument toDocument(OrderSummary summary) {
        Objects.requireNonNull(summary, "order summary is required");
        return new OrderSummaryDocument(summary.subtotal(), summary.totalTax(), summary.grandTotal(), summary.currency());
    }

    private OrderSummary toDomain(OrderSummaryDocument document) {
        Objects.requireNonNull(document, "order summary document is required");
        return new OrderSummary(document.subtotal(), document.totalTax(), document.grandTotal(), document.currency());
    }
}
