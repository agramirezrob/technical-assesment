#!/usr/bin/env bash
set -euo pipefail

payload='{"orderId":"order-demo-001","clientId":"client-001","items":[{"productId":"product-001","quantity":2,"unitPrice":100.00}]}'

printf '%s\n' "$payload" | docker compose exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:9092 \
  --topic orders-topic
