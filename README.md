# Worker reactivo de pedidos B2B

Monorepo para un flujo reactivo de pedidos: Kafka → enriquecimiento HTTP con caché Redis → cálculo de impuestos → MongoDB, con reintentos, circuit breaker y DLT.

## Estado inicial

Esta primera entrega deja creada la infraestructura, las fronteras hexagonales y los contratos. El siguiente incremento implementará la regla de impuestos como lógica de dominio pura y, después, los adaptadores del flujo completo.

## Arquitectura

```text
orders-topic
     │
     ▼
Kafka adapter ──► Application service ──► Domain (orden, enriquecimiento, impuestos)
                       │        │       │
                       │        │       └── MongoDB output port
                       │        └────────── Products / Clients output ports
                       └────────────────── Cache output port
     │
     └─ error tras reintentos ──────────► orders-dlt
```

`order-worker` organiza código por responsabilidad, no por framework:

```text
domain/        Entidades, value objects y reglas puras.
application/   Casos de uso y puertos de entrada/salida.
adapters/in/   Kafka y HTTP de entrada.
adapters/out/  HTTP, Redis y MongoDB.
config/        Wiring Spring, Kafka y Resilience4j.
```

Los adaptadores solo traducen protocolos. El servicio de aplicación orquesta `Mono`/`Flux`; no contiene configuración técnica. Las reglas tributarias vivirán exclusivamente en `domain` y usarán `BigDecimal`.

## Módulos

| Directorio | Tecnología | Responsabilidad |
| --- | --- | --- |
| `order-worker` | Java 21, Spring Boot WebFlux | Consume y procesa pedidos. |
| `products-api` | Go 1.21 | Catálogo mock, `GET /products/{productId}`. |
| `clients-api` | NestJS | Clientes mock, `GET /clients/:id`. |
| `scripts` | Bash | Publicación manual de pedidos de prueba. |

## Arranque (objetivo final)

```bash
docker compose up --build
```

Las variables disponibles están en `.env.example`. Docker Compose utiliza esos valores como defaults, por lo que no requiere un `.env` para iniciar.

## Decisiones ya fijadas

- Consumidor Kafka con `reactor-kafka`; los errores agotados se convertirán en eventos DLT con `timestamp`, causa e intento.
- Idempotencia mediante índice único de `orderId` y una comprobación explícita de estado `PROCESSED`.
- Caché por puerto (`CachePort`), implementada con Redis reactivo y TTL configurable.
- Ningún adaptador podrá invocar `block()`, `Thread.sleep()` ni APIs síncronas dentro de un pipeline reactivo.
