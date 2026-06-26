package com.b2b.orders.domain.service;

import com.b2b.orders.domain.model.Client;
import com.b2b.orders.domain.model.EnrichedOrder;
import com.b2b.orders.domain.model.EnrichedOrderLine;
import com.b2b.orders.domain.model.OrderItem;
import com.b2b.orders.domain.model.OrderReceived;
import com.b2b.orders.domain.model.Product;
import com.b2b.orders.domain.model.TaxCategory;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TaxCalculationServiceTest {
    private final TaxCalculationService service = new TaxCalculationService(
            Clock.fixed(Instant.parse("2024-09-12T10:45:03.241Z"), ZoneOffset.UTC)
    );

    @Test
    void calculatesGravadoLine() {
        EnrichedOrderLine line = service.calculateLine(
                new OrderItem("PRD-001", 24, money("3500.00")),
                product("PRD-001", TaxCategory.GRAVADO)
        );

        assertEquals(money("84000.00"), line.subtotal());
        assertEquals(new BigDecimal("0.19"), line.taxRate());
        assertEquals(money("15960.00"), line.taxAmount());
        assertEquals(money("99960.00"), line.lineTotal());
    }

    @Test
    void calculatesReducidoAndExentoLines() {
        EnrichedOrderLine reducido = service.calculateLine(
                new OrderItem("PRD-008", 12, money("8200.00")), product("PRD-008", TaxCategory.REDUCIDO)
        );
        EnrichedOrderLine exento = service.calculateLine(
                new OrderItem("PRD-010", 1, money("10000.00")), product("PRD-010", TaxCategory.EXENTO)
        );

        assertEquals(money("4920.00"), reducido.taxAmount());
        assertEquals(money("103320.00"), reducido.lineTotal());
        assertEquals(money("0.00"), exento.taxAmount());
        assertEquals(money("10000.00"), exento.lineTotal());
    }

    @Test
    void createsProcessedOrderWithFiscalClientTraceabilityAndCorrectSummary() {
        OrderReceived order = new OrderReceived(
                "ORD-2024-COL-00147", "CLI-99821", "B2B", Instant.parse("2024-09-12T10:45:00Z"),
                List.of(new OrderItem("PRD-001", 24, money("3500.00")), new OrderItem("PRD-008", 12, money("8200.00")))
        );
        Client client = new Client("CLI-99821", "Distribuidora Andina S.A.S", "MAYORISTA", "RESPONSABLE_IVA", "Valle del Cauca", "B2B");

        EnrichedOrder result = service.calculate(order, client, Map.of(
                "PRD-001", product("PRD-001", TaxCategory.GRAVADO),
                "PRD-008", product("PRD-008", TaxCategory.REDUCIDO)
        ));

        assertEquals("RESPONSABLE_IVA", result.client().taxRegime());
        assertEquals(money("182400.00"), result.summary().subtotal());
        assertEquals(money("20880.00"), result.summary().totalTax());
        assertEquals(money("203280.00"), result.summary().grandTotal());
        assertEquals("COP", result.summary().currency());
        assertEquals(Instant.parse("2024-09-12T10:45:03.241Z"), result.processedAt());
    }

    private static Product product(String id, TaxCategory taxCategory) {
        return new Product(id, "Producto " + id, "SKU-" + id, "CATEGORIA", taxCategory, "UNIT");
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
