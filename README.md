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

`OrderProcessingService` implementa el caso de uso principal: verifica idempotencia, obtiene cliente y productos por puertos reactivos, calcula la orden enriquecida con el dominio y persiste por el puerto de repositorio. Si el pedido ya existe como `PROCESSED`, termina sin consultar APIs externas ni guardar duplicados.

## Regla de impuesto base

`TaxCalculationService` es un servicio de dominio puro. Calcula cada línea con `BigDecimal` y redondeo `HALF_UP` a dos decimales: `subtotal = quantity × unitPrice`, `taxAmount = subtotal × taxRate` y `lineTotal = subtotal + taxAmount`. Las tasas son `GRAVADO` 19 %, `REDUCIDO` 5 % y `EXENTO` 0 %. El `taxRegime` del cliente se conserva en la orden enriquecida, pero no altera la tasa.

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

Las pruebas del worker se ejecutan sin una instalación global de Maven mediante `./mvnw test` (o `./mvnw.cmd test` en Windows).

## Herramientas visuales locales

Al levantar el stack también quedan disponibles:

| Herramienta | URL | Uso |
| --- | --- | --- |
| Kafka UI | `http://localhost:8083` | Ver `orders-topic`, `orders-dlt`, mensajes, consumer groups y particiones. |
| Mongo Express | `http://localhost:8084` | Ver la base `orders` y la colección `enriched-orders`. |

Puertos configurables:

```env
KAFKA_UI_PORT=8083
MONGO_EXPRESS_PORT=8084
```

## Decisiones ya fijadas

- Consumidor Kafka con `reactor-kafka`; los errores agotados se convertirán en eventos DLT con `timestamp`, causa e intento.
- Idempotencia mediante índice único de `orderId` y una comprobación explícita de estado `PROCESSED`.
- Caché por puerto (`CachePort`), implementada con Redis reactivo y TTL configurable.
- Ningún adaptador podrá invocar `block()`, `Thread.sleep()` ni APIs síncronas dentro de un pipeline reactivo.
