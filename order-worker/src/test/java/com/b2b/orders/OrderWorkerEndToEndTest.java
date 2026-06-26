package com.b2b.orders;

import com.b2b.orders.adapters.out.mongodb.document.EnrichedOrderDocument;
import com.b2b.orders.adapters.out.mongodb.repository.ReactiveEnrichedOrderMongoRepository;
import com.b2b.orders.application.port.out.ClientDirectoryPort;
import com.b2b.orders.application.port.out.ProductCatalogPort;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
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
import java.util.Map;
import java.util.Properties;

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

    @Container
    static final ConfluentKafkaContainer KAFKA = new ConfluentKafkaContainer(
            DockerImageName.parse("confluentinc/cp-kafka:7.6.1")
    );

    @Container
    static final MongoDBContainer MONGODB = new MongoDBContainer(
            DockerImageName.parse("mongo:7.0")
    );

    private final ReactiveEnrichedOrderMongoRepository repository;

    @Autowired
    OrderWorkerEndToEndTest(ReactiveEnrichedOrderMongoRepository repository) {
        this.repository = repository;
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("app.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.data.mongodb.uri", MONGODB::getReplicaSetUrl);
    }

    @Test
    void consumesKafkaOrderAndPersistsEnrichedOrderInMongo() throws Exception {
        publishOrder();

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

    private void publishOrder() throws Exception {
        String payload = """
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
                """.formatted(ORDER_ID);

        try (KafkaProducer<String, String> producer = new KafkaProducer<>(producerProperties())) {
            producer.send(new ProducerRecord<>("orders-topic", ORDER_ID, payload)).get();
        }
    }

    private Properties producerProperties() {
        Properties properties = new Properties();
        properties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers());
        properties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName());
        properties.put(ProducerConfig.ACKS_CONFIG, "all");
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
