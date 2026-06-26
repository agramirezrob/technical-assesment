#!/usr/bin/env bash
set -euo pipefail

payload='{"orderId":"ORD-2024-COL-00147","clientId":"CLI-99821","channel":"B2B","createdAt":"2024-09-12T10:45:00Z","items":[{"productId":"PRD-001","quantity":24,"unitPrice":3500.00},{"productId":"PRD-008","quantity":12,"unitPrice":8200.00}]}'

printf '%s\n' "$payload" | docker compose exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:9092 \
  --topic orders-topic
