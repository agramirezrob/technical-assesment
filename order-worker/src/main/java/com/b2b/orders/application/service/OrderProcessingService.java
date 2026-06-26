package com.b2b.orders.application.service;

import com.b2b.orders.application.port.in.ProcessOrderUseCase;
import com.b2b.orders.application.port.out.ClientDirectoryPort;
import com.b2b.orders.application.port.out.OrderRepositoryPort;
import com.b2b.orders.application.port.out.ProductCatalogPort;
import com.b2b.orders.domain.model.Client;
import com.b2b.orders.domain.model.EnrichedOrder;
import com.b2b.orders.domain.model.OrderItem;
import com.b2b.orders.domain.model.OrderReceived;
import com.b2b.orders.domain.model.Product;
import com.b2b.orders.domain.model.TaxCategory;
import com.b2b.orders.domain.service.TaxCalculationService;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;
import java.util.Objects;

public final class OrderProcessingService implements ProcessOrderUseCase {
    private final ProductCatalogPort productCatalog;
    private final ClientDirectoryPort clientDirectory;
    private final OrderRepositoryPort orderRepository;
    private final TaxCalculationService taxCalculationService;

    public OrderProcessingService(
            ProductCatalogPort productCatalog,
            ClientDirectoryPort clientDirectory,
            OrderRepositoryPort orderRepository,
            TaxCalculationService taxCalculationService
    ) {
        this.productCatalog = Objects.requireNonNull(productCatalog, "productCatalog is required");
        this.clientDirectory = Objects.requireNonNull(clientDirectory, "clientDirectory is required");
        this.orderRepository = Objects.requireNonNull(orderRepository, "orderRepository is required");
        this.taxCalculationService = Objects.requireNonNull(taxCalculationService, "taxCalculationService is required");
    }

    @Override
    public Mono<Void> process(OrderReceived order) {
        return Mono.just(Objects.requireNonNull(order, "order is required"))
                .flatMap(validOrder -> orderRepository.isProcessed(validOrder.orderId())
                        .flatMap(alreadyProcessed -> alreadyProcessed
                                ? Mono.empty()
                                : enrichCalculateAndSave(validOrder).then()
                        ));
    }

    private Mono<EnrichedOrder> enrichCalculateAndSave(OrderReceived order) {
        Mono<Client> client = clientDirectory.findById(order.clientId()).map(this::toClient);
        Mono<Map<String, Product>> productsById = Flux.fromIterable(order.items())
                .map(OrderItem::productId)
                .distinct()
                .flatMap(productCatalog::findById)
                .map(this::toProduct)
                .collectMap(this::productId);

        return Mono.zip(client, productsById)
                .map(enrichedData -> taxCalculationService.calculate(order, enrichedData.getT1(), enrichedData.getT2()))
                .flatMap(orderRepository::save);
    }

    private Product toProduct(ProductCatalogPort.ProductData data) {
        return new Product(
                data.productId(),
                data.name(),
                data.sku(),
                data.category(),
                TaxCategory.valueOf(data.taxCategory()),
                data.unitOfMeasure()
        );
    }

    private String productId(Product product) {
        return Objects.requireNonNull(product, "product is required").productId();
    }

    private Client toClient(ClientDirectoryPort.ClientData data) {
        return new Client(
                data.clientId(),
                data.name(),
                data.segment(),
                data.taxRegime(),
                data.region(),
                data.channel()
        );
    }
}
