#!/usr/bin/env bash
set -euo pipefail

ORDER_ID="${ORDER_ID:-ORD-2024-COL-00147}"
CLIENT_ID="${CLIENT_ID:-CLI-99821}"
TOPIC="${TOPIC:-orders-topic}"

while [[ $# -gt 0 ]]; do
  case "$1" in
    --order-id)
      ORDER_ID="$2"
      shift 2
      ;;
    --client-id)
      CLIENT_ID="$2"
      shift 2
      ;;
    --topic)
      TOPIC="$2"
      shift 2
      ;;
    -h|--help)
      echo "Usage: bash scripts/produce-order.sh [--order-id ID] [--client-id ID] [--topic TOPIC]"
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      exit 1
      ;;
  esac
done

payload=$(cat <<JSON
{
  "orderId": "${ORDER_ID}",
  "clientId": "${CLIENT_ID}",
  "channel": "B2B",
  "createdAt": "2024-09-12T10:45:00Z",
  "items": [
    { "productId": "PRD-001", "quantity": 24, "unitPrice": 3500.00 },
    { "productId": "PRD-008", "quantity": 12, "unitPrice": 8200.00 }
  ]
}
JSON
)

echo "Publishing order ${ORDER_ID} to ${TOPIC}"

printf '%s\n' "$payload" | docker compose exec -T kafka kafka-console-producer \
  --bootstrap-server kafka:9092 \
  --topic "$TOPIC"

echo "Published. Check Kafka UI at http://localhost:8083 and Mongo Express at http://localhost:8084"
