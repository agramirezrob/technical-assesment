package com.b2b.orders.adapters.out.http;

import com.b2b.orders.application.port.out.ProductCatalogPort;
import com.b2b.orders.config.AppProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;

import java.util.Objects;

@Component
public class ProductsWebClientAdapter implements ProductCatalogPort {
    private static final String DEPENDENCY_NAME = "products-api";

    private final WebClient webClient;
    private final CachedExternalApiClient cache;
    private final ResilientHttpClient resilientHttpClient;

    public ProductsWebClientAdapter(
            @Qualifier("productsWebClient") WebClient webClient,
            ReactiveStringRedisTemplate redisTemplate,
            ObjectMapper objectMapper,
            AppProperties properties,
            CircuitBreakerRegistry circuitBreakerRegistry,
            RetryRegistry retryRegistry
    ) {
        this.webClient = webClient;
        this.cache = new CachedExternalApiClient(redisTemplate, objectMapper, properties.cache().ttl());
        this.resilientHttpClient = new ResilientHttpClient(DEPENDENCY_NAME, circuitBreakerRegistry, retryRegistry);
    }

    @Override
    public Mono<ProductData> findById(String productId) {
        return cache.cached("products:" + productId, ProductResponse.class, () -> resilientHttpClient.execute(fetchProduct(productId)))
                .map(response -> Objects.requireNonNull(response, "product response is required").toPortData());
    }

    private Mono<ProductResponse> fetchProduct(String productId) {
        Objects.requireNonNull(productId, "productId is required");
        return webClient.get()
                .uri("/products/{productId}", productId)
                .retrieve()
                .bodyToMono(ProductResponse.class);
    }

    private record ProductResponse(
            String productId,
            String name,
            String sku,
            String category,
            String taxCategory,
            String unitOfMeasure
    ) {
        ProductData toPortData() {
            return new ProductData(productId, name, sku, category, taxCategory, unitOfMeasure);
        }
    }
}
