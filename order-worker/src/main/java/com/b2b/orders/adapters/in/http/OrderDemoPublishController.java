package com.b2b.orders.adapters.in.http;

import com.b2b.orders.adapters.out.kafka.KafkaOrderMessagePublisher;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import reactor.core.publisher.Mono;

import java.util.Objects;

@RestController
@RequestMapping(path = "/demo/orders", produces = MediaType.APPLICATION_JSON_VALUE)
public class OrderDemoPublishController {
    private final KafkaOrderMessagePublisher publisher;

    public OrderDemoPublishController(KafkaOrderMessagePublisher publisher) {
        this.publisher = publisher;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public Mono<PublishOrderResponse> publish(@RequestBody Mono<JsonNode> request) {
        return request
                .map(this::toCommand)
                .flatMap(command -> publisher.publish(command.orderId(), command.payload())
                        .thenReturn(new PublishOrderResponse(command.orderId(), publisher.ordersTopic(), "PUBLISHED")));
    }

    private PublishOrderCommand toCommand(JsonNode payload) {
        Objects.requireNonNull(payload, "order payload is required");
        JsonNode orderIdNode = payload.get("orderId");
        if (orderIdNode == null || orderIdNode.isNull() || orderIdNode.asText().isBlank()) {
            throw new IllegalArgumentException("orderId is required");
        }
        return new PublishOrderCommand(orderIdNode.asText(), payload.toString());
    }

    private record PublishOrderCommand(String orderId, String payload) {}

    public record PublishOrderResponse(String orderId, String topic, String status) {}
}
