package com.b2b.orders;

import com.b2b.orders.adapters.out.mongodb.document.EnrichedOrderDocument;
import com.b2b.orders.adapters.out.mongodb.repository.ReactiveEnrichedOrderMongoRepository;
import com.b2b.orders.application.port.out.ClientDirectoryPort;
import com.b2b.orders.application.port.out.ProductCatalogPort;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.ConfluentKafkaContainer;
import org.testcontainers.utility.DockerImageName;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.UUID;

@SpringBootTest(
        classes = {OrderWorkerApplication.class, OrderWorkerEndToEndTest.ExternalApiStubs.class},
        properties = {
                "server.port=0",
                "app.kafka.orders-topic=orders-topic",
                "app.kafka.dlt-topic=orders-dlt",
                "app.kafka.consumer-group-id=order-worker-e2e",
                "app.kafka.max-attempts=2",
                "app.kafka.retry-delay=100ms",
                "app.cache.ttl-seconds=60",
                "app.clients.products-base-url=http://localhost:1",
                "app.clients.clients-base-url=http://localhost:1",
                "spring.data.redis.host=localhost",
                "spring.data.redis.port=6379"
        }
)
@Testcontainers(disabledWithoutDocker = true)
class OrderWorkerEndToEndTest {
    private static final String ORDER_ID = "ORD-2024-COL-E2E";
    private static final String INVALID_ORDER_ID = "ORD-2024-COL-DLT";

    @Container
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Container
    static final MongoDBContainer MONGODB = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0")
    );

    private final ReactiveEnrichedOrderMongoRepository repository;
    private final ObjectMapper objectMapper;

    @Autowired
    OrderWorkerEndToEndTest(ReactiveEnrichedOrderMongoRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }

    @Test
    void consumesKafkaOrderAndPersistsEnrichedOrderInMongo() throws Exception {
        publish("orders-topic", ORDER_ID, validOrderPayload(ORDER_ID));

        Mono<EnrichedOrderDocument> persistedOrder = Mono.defer(() -> repository.findByOrderId(ORDER_ID))
                .repeatWhenEmpty(40, repeat -> repeat.delayElements(Duration.ofMillis(250)));

        StepVerifier.create(persistedOrder)
                .expectNextMatches(order ->
                        ORDER_ID.equals(order.orderId())
                                && "PROCESSED".equals(order.status())
                                && "RESPONSABLE_IVA".equals(order.client().taxRegime())
                                && new BigDecimal("182400.00").compareTo(order.summary().subtotal()) == 0
                                && new BigDecimal("20880.00").compareTo(order.summary().totalTax()) == 0
                                && new BigDecimal("203280.00").compareTo(order.summary().grandTotal()) == 0
                )
                .verifyComplete();
    }

    @Test
    void sendsInvalidKafkaMessageToDeadLetterTopic() throws Exception {
        String invalidPayload = """
                {
                  "orderId": "%s",
                  "clientId": "CLI-99821",
                  "channel": "B2B",
                  "createdAt": "2024-09-12T10:45:00Z",
                  "items": null
                }
                """.formatted(INVALID_ORDER_ID);

        publish("orders-topic", INVALID_ORDER_ID, invalidPayload);

        JsonNode deadLetter = waitForDeadLetter(INVALID_ORDER_ID);

        StepVerifier.create(repository.findByOrderId(INVALID_ORDER_ID))
                .verifyComplete();

        org.junit.jupiter.api.Assertions.assertEquals(INVALID_ORDER_ID, deadLetter.get("orderId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(2, deadLetter.get("attempt").asInt());
        org.junit.jupiter.api.Assertions.assertTrue(deadLetter.get("cause").asText().contains("items must not be empty"));
        org.junit.jupiter.api.Assertions.assertEquals(invalidPayload, deadLetter.get("originalPayload").asText());
    }

    private String validOrderPayload(String orderId) {
        return """
                {
                  "orderId": "%s",
                  "clientId": "CLI-99821",
                  "channel": "B2B",
                  "createdAt": "2024-09-12T10:45:00Z",
                  "items": [
                    { "productId": "PRD-001", "quantity": 24, "unitPrice": 3500.00 },
                    { "productId": "PRD-008", "quantity": 12, "unitPrice": 8200.00 }
                  ]
                }
                """.formatted(orderId);
    }

    private void publish(String topic, String key, String payload) throws Exception {
        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties())) {
            producer.send(new ProducerRecord<>(topic, key, payload)).get();
        }
    }

    private JsonNode waitForDeadLetter(String orderId) throws Exception {
        try (KafkaConsumer<String, String> consumer = new KafkaConsumer<>(consumerProperties())) {
            consumer.subscribe(List.of("orders-dlt"));
            Instant deadline = Instant.now().plus(Duration.ofSeconds(15));
            while (Instant.now().isBefore(deadline)) {
                ConsumerRecords<String, String> records = consumer.poll(Duration.ofMillis(300));
                for (ConsumerRecord<String, String> record : records) {
                    JsonNode value = objectMapper.readTree(record.value());
                    if (orderId.equals(value.get("orderId").asText())) {
                        return value;
                    }
                }
            }
        }
        throw new AssertionError("Expected DLT message for orderId " + orderId);
    }

    private Properties producerProperties() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
        return properties;
    }

    private Properties consumerProperties() {
        Properties properties = new Properties();
        properties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ConsumerConfig.GROUP_ID_CONFIG, "order-worker-e2e-dlt-" + UUID.randomUUID());
        properties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName());
        properties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        properties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, "true");
        return properties;
    }

    @TestConfiguration
    static class ExternalApiStubs {
        @Bean
        @Primary
        ProductCatalogPort productCatalogPort() {
            Map<String, ProductCatalogPort.ProductData> products = Map.of(
                    "PRD-001", new ProductCatalogPort.ProductData("PRD-001", "Gaseosa 600ml", "GAS-600-PET", "BEBIDAS", "GRAVADO", "UNIT"),
                    "PRD-008", new ProductCatalogPort.ProductData("PRD-008", "Leche descremada 1L", "LEC-1L", "LACTEOS", "REDUCIDO", "UNIT")
            );
            return productId -> Mono.just(products.get(productId));
        }

        @Bean
        @Primary
        ClientDirectoryPort clientDirectoryPort() {
            return clientId -> Mono.just(new ClientDirectoryPort.ClientData(
                    "CLI-99821",
                    "Distribuidora Andina S.A.S",
                    "MAYORISTA",
                    "RESPONSABLE_IVA",
                    "Valle del Cauca",
                    "B2B"
            ));
        }
    }
}
