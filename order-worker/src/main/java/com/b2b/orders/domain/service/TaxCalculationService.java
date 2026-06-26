package com.b2b.orders.domain.service;

import com.b2b.orders.domain.model.Client;
import com.b2b.orders.domain.model.EnrichedOrder;
import com.b2b.orders.domain.model.EnrichedOrderLine;
import com.b2b.orders.domain.model.OrderItem;
import com.b2b.orders.domain.model.OrderReceived;
import com.b2b.orders.domain.model.OrderStatus;
import com.b2b.orders.domain.model.OrderSummary;
import com.b2b.orders.domain.model.Product;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public final class TaxCalculationService {
    public static final String CURRENCY = "COP";
    private static final int MONEY_SCALE = 2;

    private final Clock clock;

    public TaxCalculationService(Clock clock) {
        this.clock = clock;
    }

    public EnrichedOrderLine calculateLine(OrderItem item, Product product) {
        BigDecimal subtotal = money(item.unitPrice().multiply(BigDecimal.valueOf(item.quantity())));
        BigDecimal taxAmount = money(subtotal.multiply(product.taxCategory().rate()));
        BigDecimal lineTotal = money(subtotal.add(taxAmount));

        return new EnrichedOrderLine(
                item.productId(), product.name(), product.sku(), product.taxCategory(),
                item.quantity(), item.unitPrice(), subtotal, product.taxCategory().rate(), taxAmount, lineTotal
        );
    }

    public EnrichedOrder calculate(OrderReceived order, Client client, Map<String, Product> productsById) {
        List<EnrichedOrderLine> lines = order.items().stream()
                .map(item -> calculateLine(item, productFor(item, productsById)))
                .toList();

        BigDecimal subtotal = lines.stream()
                .map(line -> Objects.requireNonNull(line, "enriched order line is required").subtotal())
                .reduce(BigDecimal.ZERO, this::addAmounts);
        BigDecimal totalTax = lines.stream()
                .map(line -> Objects.requireNonNull(line, "enriched order line is required").taxAmount())
                .reduce(BigDecimal.ZERO, this::addAmounts);
        BigDecimal grandTotal = lines.stream()
                .map(line -> Objects.requireNonNull(line, "enriched order line is required").lineTotal())
                .reduce(BigDecimal.ZERO, this::addAmounts);

        OrderSummary summary = new OrderSummary(money(subtotal), money(totalTax), money(grandTotal), CURRENCY);
        return new EnrichedOrder(order.orderId(), OrderStatus.PROCESSED, client, lines, summary, clock.instant());
    }

    private Product productFor(OrderItem item, Map<String, Product> productsById) {
        Product product = productsById.get(item.productId());
        if (product == null) {
            throw new IllegalArgumentException("Product was not enriched: " + item.productId());
        }
        return product;
    }

    private BigDecimal money(BigDecimal amount) {
        return amount.setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private BigDecimal addAmounts(BigDecimal accumulated, BigDecimal amount) {
        return Objects.requireNonNull(accumulated, "accumulated amount is required")
                .add(Objects.requireNonNull(amount, "line amount is required"));
    }
}
