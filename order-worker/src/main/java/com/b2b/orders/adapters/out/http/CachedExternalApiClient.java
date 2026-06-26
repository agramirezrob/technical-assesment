package com.b2b.orders.adapters.out.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import reactor.core.publisher.Mono;

import java.time.Duration;
import java.util.Objects;
import java.util.function.Supplier;

final class CachedExternalApiClient {
    private final ReactiveStringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final Duration ttl;

    CachedExternalApiClient(ReactiveStringRedisTemplate redisTemplate, ObjectMapper objectMapper, Duration ttl) {
        this.redisTemplate = Objects.requireNonNull(redisTemplate, "redisTemplate is required");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper is required");
        this.ttl = Objects.requireNonNull(ttl, "cache ttl is required");
    }

    <T> Mono<T> cached(String key, Class<T> type, Supplier<Mono<T>> source) {
        Objects.requireNonNull(key, "cache key is required");
        Objects.requireNonNull(type, "cache value type is required");
        Objects.requireNonNull(source, "cache source is required");
        return redisTemplate.opsForValue()
                .get(key)
                .flatMap(cachedValue -> deserialize(Objects.requireNonNull(cachedValue, "cached value is required"), type))
                .onErrorResume(error -> Mono.empty())
                .switchIfEmpty(Mono.defer(source).flatMap(value -> writeThrough(key, Objects.requireNonNull(value, "source value is required"))));
    }

    private <T> Mono<T> deserialize(String value, Class<T> type) {
        try {
            return Mono.just(objectMapper.readValue(value, type));
        } catch (JsonProcessingException error) {
            return Mono.error(error);
        }
    }

    private <T> Mono<T> writeThrough(String key, T value) {
        String safeKey = Objects.requireNonNull(key, "cache key is required");
        T safeValue = Objects.requireNonNull(value, "cache value is required");
        Duration safeTtl = Objects.requireNonNull(ttl, "cache ttl is required");
        try {
            String json = Objects.requireNonNull(objectMapper.writeValueAsString(safeValue), "serialized cache value is required");
            return redisTemplate.opsForValue()
                    .set(safeKey, json, safeTtl)
                    .thenReturn(safeValue)
                    .onErrorReturn(safeValue);
        } catch (JsonProcessingException error) {
            return Mono.just(safeValue);
        }
    }
}
