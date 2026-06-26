package com.b2b.orders.adapters.out.http;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.reactor.circuitbreaker.operator.CircuitBreakerOperator;
import io.github.resilience4j.reactor.retry.RetryOperator;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import reactor.core.publisher.Mono;

import java.util.Objects;

final class ResilientHttpClient {
    private final CircuitBreaker circuitBreaker;
    private final Retry retry;

    ResilientHttpClient(String dependencyName, CircuitBreakerRegistry circuitBreakerRegistry, RetryRegistry retryRegistry) {
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker(dependencyName);
        this.retry = retryRegistry.retry(dependencyName);
    }

    <T> Mono<T> execute(Mono<T> request) {
        return Objects.requireNonNull(request, "request is required")
                .transformDeferred(RetryOperator.of(retry))
                .transformDeferred(CircuitBreakerOperator.of(circuitBreaker));
    }
}
