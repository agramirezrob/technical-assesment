package com.b2b.orders.domain.model;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OrderValidationTest {

    @Test
    void rejectsInvalidOrderItems() {
        assertThrows(IllegalArgumentException.class, () -> new OrderItem("", 1, money("1000.00")));
        assertThrows(IllegalArgumentException.class, () -> new OrderItem("PRD-001", 0, money("1000.00")));
        assertThrows(NullPointerException.class, () -> new OrderItem("PRD-001", 1, null));
        assertThrows(IllegalArgumentException.class, () -> new OrderItem("PRD-001", 1, money("-1.00")));
    }

    @Test
    void rejectsInvalidOrders() {
        List<OrderItem> items = List.of(new OrderItem("PRD-001", 1, money("1000.00")));

        assertThrows(IllegalArgumentException.class, () -> new OrderReceived("", "CLI-99821", "B2B", Instant.now(), items));
        assertThrows(IllegalArgumentException.class, () -> new OrderReceived("ORD-001", "", "B2B", Instant.now(), items));
        assertThrows(IllegalArgumentException.class, () -> new OrderReceived("ORD-001", "CLI-99821", "", Instant.now(), items));
        assertThrows(NullPointerException.class, () -> new OrderReceived("ORD-001", "CLI-99821", "B2B", null, items));
        assertThrows(IllegalArgumentException.class, () -> new OrderReceived("ORD-001", "CLI-99821", "B2B", Instant.now(), List.of()));
    }

    @Test
    void copiesItemsDefensively() {
        ArrayList<OrderItem> items = new ArrayList<>();
        items.add(new OrderItem("PRD-001", 1, money("1000.00")));

        OrderReceived order = new OrderReceived("ORD-001", "CLI-99821", "B2B", Instant.now(), items);
        items.clear();

        assertEquals(1, order.items().size());
        assertThrows(UnsupportedOperationException.class, () -> order.items().add(new OrderItem("PRD-002", 1, money("2000.00"))));
    }

    private static BigDecimal money(String value) {
        return new BigDecimal(value);
    }
}
