package com.b2b.orders.application.service;

import com.b2b.orders.application.port.out.ClientDirectoryPort;
import com.b2b.orders.application.port.out.OrderRepositoryPort;
import com.b2b.orders.application.port.out.ProductCatalogPort;
import com.b2b.orders.domain.model.EnrichedOrder;
import com.b2b.orders.domain.model.OrderItem;
import com.b2b.orders.domain.model.OrderReceived;
import com.b2b.orders.domain.service.TaxCalculationService;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OrderProcessingServiceTest {
    private final ProductCatalogStub productCatalog = new ProductCatalogStub();
    private final ClientDirectoryStub clientDirectory = new ClientDirectoryStub();
    private final OrderRepositoryStub orderRepository = new OrderRepositoryStub();
    private final OrderProcessingService service = new OrderProcessingService(
            productCatalog,
            clientDirectory,
            orderRepository,
            new TaxCalculationService(Clock.fixed(Instant.parse("2024-09-12T10:45:03.241Z"), ZoneOffset.UTC))
    );

    @Test
    void enrichesCalculatesAndPersistsOrder() {
        StepVerifier.create(service.process(sampleOrder()))
                .verifyComplete();

        assertEquals(1, orderRepository.savedOrders.size());
        EnrichedOrder saved = orderRepository.savedOrders.getFirst();
        assertEquals("ORD-2024-COL-00147", saved.orderId());
        assertEquals("RESPONSABLE_IVA", saved.client().taxRegime());
        assertEquals(new BigDecimal("182400.00"), saved.summary().subtotal());
        assertEquals(new BigDecimal("20880.00"), saved.summary().totalTax());
        assertEquals(new BigDecimal("203280.00"), saved.summary().grandTotal());
    }

    @Test
    void ignoresOrderWhenAlreadyProcessed() {
        orderRepository.processedOrders.put("ORD-2024-COL-00147", true);

        StepVerifier.create(service.process(sampleOrder()))
                .verifyComplete();

        assertTrue(orderRepository.savedOrders.isEmpty());
        assertEquals(0, productCatalog.calls.get());
        assertEquals(0, clientDirectory.calls.get());
    }

    @Test
    void requestsDuplicatedProductOnlyOnceInsideSameOrder() {
        OrderReceived order = new OrderReceived(
                "ORD-2024-COL-00148",
                "CLI-99821",
                "B2B",
                Instant.parse("2024-09-12T10:45:00Z"),
                List.of(
                        new OrderItem("PRD-001", 1, new BigDecimal("1000.00")),
                        new OrderItem("PRD-001", 2, new BigDecimal("1000.00"))
                )
        );

        StepVerifier.create(service.process(order))
                .verifyComplete();

        assertEquals(1, productCatalog.calls.get());
        assertEquals(1, orderRepository.savedOrders.size());
    }

    private OrderReceived sampleOrder() {
        return new OrderReceived(
                "ORD-2024-COL-00147",
                "CLI-99821",
                "B2B",
                Instant.parse("2024-09-12T10:45:00Z"),
                List.of(
                        new OrderItem("PRD-001", 24, new BigDecimal("3500.00")),
                        new OrderItem("PRD-008", 12, new BigDecimal("8200.00"))
                )
        );
    }

    private static final class ProductCatalogStub implements ProductCatalogPort {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Mono<ProductData> findById(String productId) {
            calls.incrementAndGet();
            ProductData product = switch (productId) {
                case "PRD-001" -> new ProductData("PRD-001", "Gaseosa 600ml", "GAS-600-PET", "BEBIDAS", "GRAVADO", "UNIT");
                case "PRD-008" -> new ProductData("PRD-008", "Leche descremada 1L", "LEC-1L", "LACTEOS", "REDUCIDO", "UNIT");
                default -> throw new IllegalArgumentException("Unexpected productId " + productId);
            };
            return Mono.just(product);
        }
    }

    private static final class ClientDirectoryStub implements ClientDirectoryPort {
        private final AtomicInteger calls = new AtomicInteger();

        @Override
        public Mono<ClientData> findById(String clientId) {
            calls.incrementAndGet();
            return Mono.just(new ClientData("CLI-99821", "Distribuidora Andina S.A.S", "MAYORISTA", "RESPONSABLE_IVA", "Valle del Cauca", "B2B"));
        }
    }

    private static final class OrderRepositoryStub implements OrderRepositoryPort {
        private final Map<String, Boolean> processedOrders = new ConcurrentHashMap<>();
        private final List<EnrichedOrder> savedOrders = new ArrayList<>();

        @Override
        public Mono<Boolean> isProcessed(String orderId) {
            return Mono.just(processedOrders.getOrDefault(orderId, false));
        }

        @Override
        public Mono<EnrichedOrder> save(EnrichedOrder order) {
            savedOrders.add(order);
            processedOrders.put(order.orderId(), true);
            return Mono.just(order);
        }
    }
}
