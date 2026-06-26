package com.b2b.orders.adapters.in.kafka;

import com.b2b.orders.application.port.in.ProcessOrderUseCase;
import com.b2b.orders.config.AppProperties;
import com.b2b.orders.domain.model.OrderReceived;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import reactor.core.Disposable;
import reactor.core.publisher.Mono;
import reactor.kafka.receiver.KafkaReceiver;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.receiver.ReceiverRecord;
import reactor.util.retry.Retry;

import jakarta.annotation.PreDestroy;
import java.util.List;
import java.util.Objects;

@Component
public class OrderKafkaConsumerAdapter {
    private final ReceiverOptions<String, String> receiverOptions;
    private final ProcessOrderUseCase processOrderUseCase;
    private final KafkaDeadLetterPublisher deadLetterPublisher;
    private final ObjectMapper objectMapper;
    private final AppProperties.Kafka kafkaProperties;
    private Disposable subscription;

    public OrderKafkaConsumerAdapter(
            ReceiverOptions<String, String> receiverOptions,
            ProcessOrderUseCase processOrderUseCase,
            KafkaDeadLetterPublisher deadLetterPublisher,
            ObjectMapper objectMapper,
            AppProperties properties
    ) {
        this.receiverOptions = receiverOptions;
        this.processOrderUseCase = processOrderUseCase;
        this.deadLetterPublisher = deadLetterPublisher;
        this.objectMapper = objectMapper;
        this.kafkaProperties = properties.kafka();
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        ReceiverOptions<String, String> subscribedOptions = receiverOptions.subscription(List.of(kafkaProperties.ordersTopic()));
        subscription = KafkaReceiver.create(subscribedOptions)
                .receive()
                .concatMap(this::handleRecord)
                .subscribe();
    }

    @PreDestroy
    public void stop() {
        if (subscription != null) {
            subscription.dispose();
        }
    }

    private Mono<Void> handleRecord(ReceiverRecord<String, String> record) {
        String payload = Objects.requireNonNull(record.value(), "kafka payload is required");
        return processPayload(payload)
                .retryWhen(Retry.fixedDelay(kafkaProperties.maxAttempts() - 1L, kafkaProperties.retryDelay()))
                .onErrorResume(error -> deadLetterPublisher.publish(payload, error, kafkaProperties.maxAttempts()))
                .doOnSuccess(ignored -> record.receiverOffset().acknowledge());
    }

    private Mono<Void> processPayload(String payload) {
        return Mono.fromCallable(() -> toDomain(payload))
                .flatMap(processOrderUseCase::process);
    }

    private OrderReceived toDomain(String payload) throws Exception {
        return objectMapper.readValue(payload, OrderKafkaMessage.class).toDomain();
    }
}
