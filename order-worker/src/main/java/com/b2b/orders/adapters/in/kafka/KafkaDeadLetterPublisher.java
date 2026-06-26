package com.b2b.orders.adapters.in.kafka;

import com.b2b.orders.config.AppProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import jakarta.annotation.PreDestroy;
import java.time.Clock;
import java.util.Objects;

@Component
final class KafkaDeadLetterPublisher {
    private final KafkaSender<String, String> sender;
    private final ObjectMapper objectMapper;
    private final Clock clock;
    private final String dltTopic;

    KafkaDeadLetterPublisher(SenderOptions<String, String> senderOptions, ObjectMapper objectMapper, Clock clock, AppProperties properties) {
        this.sender = KafkaSender.create(senderOptions);
        this.objectMapper = objectMapper;
        this.clock = clock;
        this.dltTopic = properties.kafka().dltTopic();
    }

    Mono<Void> publish(String originalPayload, Throwable error, int attempt) {
        return Mono.fromCallable(() -> buildRecord(originalPayload, error, attempt))
                .flatMap(record -> sender.send(Mono.just(SenderRecord.create(record, null))).then());
    }

    @PreDestroy
    void close() {
        sender.close();
    }

    private ProducerRecord<String, String> buildRecord(String originalPayload, Throwable error, int attempt) throws Exception {
        String payload = Objects.requireNonNull(originalPayload, "original payload is required");
        Throwable safeError = Objects.requireNonNull(error, "error is required");
        String orderId = extractOrderId(payload);
        DeadLetterMessage message = new DeadLetterMessage(
                orderId,
                payload,
                clock.instant(),
                safeError.getClass().getName(),
                safeError.getMessage(),
                attempt
        );
        return new ProducerRecord<>(dltTopic, orderId, objectMapper.writeValueAsString(message));
    }

    private String extractOrderId(String payload) {
        try {
            JsonNode root = objectMapper.readTree(payload);
            JsonNode orderId = root.get("orderId");
            return orderId == null || orderId.isNull() ? null : orderId.asText();
        } catch (Exception ignored) {
            return null;
        }
    }
}
