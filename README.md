# Worker reactivo de pedidos B2B

Monorepo para procesar pedidos B2B de punta a punta:

```text
Kafka orders-topic
      |
      v
order-worker Java / WebFlux
      |
      +--> Products API Go + Redis cache
      +--> Clients API NestJS + Redis cache
      |
      v
MongoDB enriched-orders
      |
      +--> orders-dlt cuando el error es irrecuperable

Dashboard Angular/Nx
      |
      +--> lista productos/clientes mock
      +--> publica pedidos demo al worker
```

El stack completo levanta con Docker Compose y no requiere servicios externos.

## Ejecución con un solo comando

Desde la raíz del repositorio:

```bash
docker compose up --build
```

Ese comando construye y levanta:

| Servicio | Descripción | Puerto host |
| --- | --- | --- |
| `zookeeper` | Infraestructura Kafka | interno |
| `kafka` | Broker con `orders-topic` y `orders-dlt` | `9094` |
| `kafka-init` | Crea tópicos requeridos | interno |
| `mongodb` | Persistencia de órdenes enriquecidas | `27017` |
| `redis` | Caché de APIs externas | `6379` |
| `products-api` | API Go de productos mock | `8081` |
| `clients-api` | API NestJS de clientes mock | `8082` |
| `order-worker` | Worker Java reactivo | `8080` |
| `kafka-ui` | UI para Kafka | `8083` |
| `mongo-express` | UI para MongoDB | `8084` |
| `dashboard-ui` | Dashboard Angular/Nx para publicar pedidos demo | `8085` |

Herramientas visuales:

- Kafka UI: http://localhost:8083
- Mongo Express: http://localhost:8084
- Dashboard: http://localhost:8085
- Worker health: http://localhost:8080/actuator/health

## Dependencias de inicio

`order-worker` no arranca hasta que sus dependencias están listas. En `docker-compose.yml` se usa `depends_on` con `healthcheck`:

- `kafka-init` debe completar la creación de `orders-topic` y `orders-dlt`.
- `mongodb` debe responder `ping`.
- `redis` debe responder `PONG`.
- `products-api` debe responder `/health`.
- `clients-api` debe responder `/health`.
- `dashboard-ui` espera a que `order-worker`, `products-api` y `clients-api` estén saludables.

## Arquitectura hexagonal

El worker separa dominio, aplicación e infraestructura:

```text
order-worker/src/main/java/com/b2b/orders
  domain/        Modelos y reglas puras de negocio.
  application/   Caso de uso y puertos de entrada/salida.
  adapters/in/   Adaptador Kafka.
  adapters/out/  Adaptadores HTTP, MongoDB y Redis.
  config/        Configuración Spring, Kafka, WebClient y Resilience4j.
```

Responsabilidades principales:

- `OrderKafkaConsumerAdapter`: recibe mensajes Kafka, deserializa y delega al caso de uso.
- `OrderProcessingService`: orquesta idempotencia, enriquecimiento, cálculo y persistencia.
- `TaxCalculationService`: calcula impuestos con `BigDecimal`.
- `ProductsWebClientAdapter` y `ClientsWebClientAdapter`: consultan APIs externas de forma reactiva, con caché Redis y Resilience4j.
- `MongoOrderRepositoryAdapter`: persiste en MongoDB reactivo y protege idempotencia.
- `KafkaDeadLetterPublisher`: publica errores irrecuperables en `orders-dlt`.
- `OrderDemoPublishController`: endpoint HTTP de demo usado por el dashboard para publicar mensajes en Kafka sin saltarse el pipeline.
- `dashboard-ui`: frontend Angular 20/Nx que consume los catálogos mock y construye pedidos de prueba.


## Flujo funcional

1. `order-worker` consume un JSON desde `orders-topic`.
2. Valida campos obligatorios: `orderId`, `clientId`, `items` no nulo/no vacío y datos de cada ítem.
3. Verifica idempotencia en MongoDB: si ya existe `orderId` con estado `PROCESSED`, descarta el mensaje sin error.
4. Consulta Products API por cada producto distinto del pedido.
5. Consulta Clients API por el cliente.
6. Cachea respuestas externas en Redis con TTL configurable.
7. Calcula subtotal, impuesto y total por línea.
8. Persiste el documento enriquecido en `orders.enriched-orders`.
9. Si el procesamiento falla tras reintentos, publica el payload original en `orders-dlt` con metadata del error.

El dashboard es una herramienta de demostración: no procesa ni persiste órdenes. Solo publica pedidos en Kafka a través del worker para que el flujo real siga siendo `Kafka → Worker → APIs externas/Redis → MongoDB`.


El `taxRegime` del cliente no modifica la tasa, pero se incluye en el documento final para trazabilidad fiscal.

## Variables de entorno

Todas las URLs, puertos y TTLs son configurables por variables de entorno. Los defaults están en `.env.example` y en `docker-compose.yml`.

| Variable | Default | Uso |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `kafka:9092` | Bootstrap servers usados por el worker dentro de Docker. |
| `KAFKA_ORDERS_TOPIC` | `orders-topic` | Tópico principal de entrada. |
| `KAFKA_DLT_TOPIC` | `orders-dlt` | Tópico dead letter. |
| `KAFKA_CONSUMER_GROUP_ID` | `order-worker` | Consumer group del worker. |
| `KAFKA_MAX_ATTEMPTS` | `3` | Intentos antes de publicar en DLT. |
| `MONGODB_URI` | `mongodb://mongodb:27017/orders` | URI MongoDB del worker. |
| `REDIS_HOST` | `redis` | Host Redis dentro de Docker. |
| `REDIS_PORT` | `6379` | Puerto Redis dentro de Docker. |
| `PRODUCTS_API_BASE_URL` | `http://products-api:8081` | URL Products API. |
| `CLIENTS_API_BASE_URL` | `http://clients-api:8082` | URL Clients API. |
| `CACHE_TTL_SECONDS` | `300` | TTL del caché Redis. |
| `WORKER_PORT` | `8080` | Puerto expuesto del worker. |
| `PRODUCTS_API_PORT` | `8081` | Puerto expuesto de Products API. |
| `CLIENTS_API_PORT` | `8082` | Puerto expuesto de Clients API. |
| `MONGODB_PORT` | `27017` | Puerto expuesto de MongoDB. |
| `REDIS_PORT_HOST` | `6379` | Puerto expuesto de Redis. |
| `KAFKA_PORT` | `9094` | Puerto Kafka para acceso desde host. |
| `KAFKA_UI_PORT` | `8083` | Puerto Kafka UI. |
| `MONGO_EXPRESS_PORT` | `8084` | Puerto Mongo Express. |
| `DASHBOARD_PORT` | `8085` | Puerto del dashboard Angular/Nx. |

## Producir un mensaje de prueba

Primero levanta el stack:

```bash
docker compose up --build
```

En otra terminal, publica un pedido.

Opción visual:

```text
http://localhost:8085
```

Desde el dashboard puedes seleccionar un cliente mock, elegir productos mock, publicar la orden y luego verificarla en Kafka UI o Mongo Express.

Windows:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\scripts\produce-order.ps1
```

macOS / Linux:

```bash
bash scripts/produce-order.sh
```

Para probar idempotencia, publica dos veces el mismo `orderId`.

Windows:

```powershell
powershell.exe -ExecutionPolicy Bypass -File .\scripts\produce-order.ps1 -OrderId ORD-IDEMPOTENCY-001
powershell.exe -ExecutionPolicy Bypass -File .\scripts\produce-order.ps1 -OrderId ORD-IDEMPOTENCY-001
```

macOS / Linux:

```bash
bash scripts/produce-order.sh --order-id ORD-IDEMPOTENCY-001
bash scripts/produce-order.sh --order-id ORD-IDEMPOTENCY-001
```

Luego verifica:

- Kafka UI: `orders-topic` recibió el mensaje.
- Mongo Express: base `orders`, colección `enriched-orders`.
- Redis:

```bash
docker compose exec redis redis-cli
KEYS products:*
KEYS clients:*
TTL products:PRD-001
```

## Tests

El worker incluye tests unitarios y de integración.

Unitarios:

- `TaxCalculationServiceTest`: lógica de impuestos, totales y precisión decimal.
- `OrderProcessingServiceTest`: enriquecimiento, idempotencia y reutilización de productos repetidos.

Integración con Testcontainers:

- `OrderWorkerEndToEndTest`: levanta Kafka y MongoDB reales, produce un mensaje en Kafka y verifica el documento persistido en MongoDB.
- También valida DLT con un mensaje inválido (`items: null`): el pedido no se guarda en Mongo y se publica en `orders-dlt`.

Ejecutar tests en macOS / Linux:

```bash
cd order-worker
./mvnw test
```

Ejecutar tests en Windows:

```powershell
cd order-worker
$env:DOCKER_HOST='npipe:////./pipe/dockerDesktopLinuxEngine'
.\mvnw.cmd test
```

El reporte de cobertura JaCoCo se genera en:

```text
order-worker/target/site/jacoco/index.html
```

## Decisiones técnicas

- Programación reactiva con Spring WebFlux, Reactor Kafka, Reactive MongoDB y Redis reactivo.
- Arquitectura hexagonal para aislar reglas de negocio de Kafka, HTTP, Redis y MongoDB.
- Idempotencia por `orderId` mediante verificación explícita de estado `PROCESSED` e índice único en MongoDB.
- Cálculos monetarios con `BigDecimal`, nunca `double`.
- Resilience4j aplica retry y circuit breaker alrededor de llamadas HTTP externas.
- Redis cachea productos y clientes con TTL configurable.
- DLT incluye `orderId`, payload original, timestamp, clase de error, causa raíz e intento.
