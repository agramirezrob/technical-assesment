package com.b2b.orders.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.Objects;

@ConfigurationProperties(prefix = "app")
public record AppProperties(
        Kafka kafka,
        Clients clients,
        Cache cache
) {
    public AppProperties {
        Objects.requireNonNull(kafka, "kafka properties are required");
        Objects.requireNonNull(clients, "clients properties are required");
        Objects.requireNonNull(cache, "cache properties are required");
    }

    public record Kafka(
            String bootstrapServers,
            String ordersTopic,
            String dltTopic,
            String consumerGroupId,
            int maxAttempts,
            Duration retryDelay
    ) {
        public Kafka {
            requireText(bootstrapServers, "bootstrapServers is required");
            requireText(ordersTopic, "ordersTopic is required");
            requireText(dltTopic, "dltTopic is required");
            requireText(consumerGroupId, "consumerGroupId is required");
            if (maxAttempts <= 0) {
                throw new IllegalArgumentException("maxAttempts must be greater than zero");
            }
            Objects.requireNonNull(retryDelay, "retryDelay is required");
        }
    }

    public record Clients(String productsBaseUrl, String clientsBaseUrl) {
        public Clients {
            requireText(productsBaseUrl, "productsBaseUrl is required");
            requireText(clientsBaseUrl, "clientsBaseUrl is required");
        }
    }

    public record Cache(long ttlSeconds) {
        public Cache {
            if (ttlSeconds <= 0) {
                throw new IllegalArgumentException("ttlSeconds must be greater than zero");
            }
        }

        public Duration ttl() {
            return Duration.ofSeconds(ttlSeconds);
        }
    }

    private static void requireText(String value, String message) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(message);
        }
    }
}
