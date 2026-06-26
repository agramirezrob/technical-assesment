# Decisiones de arquitectura

## Puertos de entrada

- `ProcessOrderUseCase`: procesa un `OrderReceived` de manera reactiva.

## Puertos de salida

- `ProductCatalogPort`: obtiene los productos requeridos.
- `ClientDirectoryPort`: obtiene el cliente.
- `OrderRepositoryPort`: verifica idempotencia y persiste el pedido enriquecido.
- `CachePort`: encapsula la caché reactiva y su TTL.
- `DeadLetterPort`: publica un fallo procesable en `orders-dlt`.

## Invariantes

1. Un pedido `PROCESSED` nunca se vuelve a persistir.
2. Los importes monetarios se representan con `BigDecimal`; nunca `double` o `float`.
3. El dominio no depende de Spring, Kafka, MongoDB, Redis, HTTP ni DTOs de transporte.
4. La metadata de DLT se modela explícitamente, sin perder el mensaje original.

## Secuencia objetivo

1. Kafka recibe y valida el mensaje.
2. El caso de uso consulta idempotencia.
3. Cliente y productos se consultan de forma no bloqueante y cacheada.
4. El dominio enriquece y calcula los totales.
5. Mongo guarda de forma reactiva.
6. Un error irrecuperable produce DLT con contexto.
