package com.b2b.orders.adapters.out.http;

import com.b2b.orders.application.port.out.ClientDirectoryPort;
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
public class ClientsWebClientAdapter implements ClientDirectoryPort {
    private static final String DEPENDENCY_NAME = "clients-api";

    private final WebClient webClient;
    private final CachedExternalApiClient cache;
    private final ResilientHttpClient resilientHttpClient;

    public ClientsWebClientAdapter(
            @Qualifier("clientsWebClient") WebClient webClient,
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
    public Mono<ClientData> findById(String clientId) {
        return cache.cached("clients:" + clientId, ClientResponse.class, () -> resilientHttpClient.execute(fetchClient(clientId)))
                .map(response -> Objects.requireNonNull(response, "client response is required").toPortData());
    }

    private Mono<ClientResponse> fetchClient(String clientId) {
        Objects.requireNonNull(clientId, "clientId is required");
        return webClient.get()
                .uri("/clients/{clientId}", clientId)
                .retrieve()
                .bodyToMono(ClientResponse.class);
    }

    private record ClientResponse(
            String clientId,
            String name,
            String segment,
            String taxRegime,
            String region,
            String channel
    ) {
        ClientData toPortData() {
            return new ClientData(clientId, name, segment, taxRegime, region, channel);
        }
    }
}
