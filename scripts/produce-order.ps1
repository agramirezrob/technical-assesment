param(
    [string]$OrderId = "ORD-2024-COL-00147",
    [string]$ClientId = "CLI-99821",
    [string]$Topic = "orders-topic"
)

$ErrorActionPreference = "Stop"

$payload = @{
    orderId = $OrderId
    clientId = $ClientId
    channel = "B2B"
    createdAt = "2024-09-12T10:45:00Z"
    items = @(
        @{
            productId = "PRD-001"
            quantity = 24
            unitPrice = 3500.00
        },
        @{
            productId = "PRD-008"
            quantity = 12
            unitPrice = 8200.00
        }
    )
} | ConvertTo-Json -Depth 5 -Compress

Write-Host "Publishing order $OrderId to $Topic"

$payload | docker compose exec -T kafka kafka-console-producer `
    --bootstrap-server kafka:9092 `
    --topic $Topic

Write-Host "Published. Check Kafka UI at http://localhost:8083 and Mongo Express at http://localhost:8084"
