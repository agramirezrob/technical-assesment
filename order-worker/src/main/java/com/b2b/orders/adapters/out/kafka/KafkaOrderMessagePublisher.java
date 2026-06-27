package com.b2b.orders.adapters.out.kafka;

import com.b2b.orders.config.AppProperties;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.stereotype.Component;
import reactor.core.publisher.Mono;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;
import reactor.kafka.sender.SenderRecord;

import jakarta.annotation.PreDestroy;
import java.util.Objects;

@Component
public final class KafkaOrderMessagePublisher {
    private final KafkaSender<String, String> sender;
    private final String ordersTopic;

    public KafkaOrderMessagePublisher(SenderOptions<String, String> senderOptions, AppProperties properties) {
        this.sender = KafkaSender.create(senderOptions);
        this.ordersTopic = properties.kafka().ordersTopic();
    }

    public Mono<Void> publish(String orderId, String payload) {
        String safeOrderId = Objects.requireNonNull(orderId, "orderId is required");
        String safePayload = Objects.requireNonNull(payload, "payload is required");
        ProducerRecord<String, String> record = new ProducerRecord<>(ordersTopic, safeOrderId, safePayload);
        return sender.send(Mono.just(SenderRecord.create(record, null))).then();
    }

    public String ordersTopic() {
        return ordersTopic;
    }

    @PreDestroy
    void close() {
        sender.close();
    }
}
