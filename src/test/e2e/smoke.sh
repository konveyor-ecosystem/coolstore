#!/usr/bin/env bash
# E2E smoke: browse -> add -> checkout -> orders. Usage: smoke.sh [base-url]
set -euo pipefail
B=${1:-http://localhost:8080}
curl -fsS "$B/q/health/ready" >/dev/null
curl -fsS "$B/services/products" | grep -q 329299
curl -fsS -X POST "$B/services/cart/smoke/329299/1" | grep -q cartTotal
curl -fsS -X POST "$B/services/cart/checkout/smoke" >/dev/null
sleep 2; curl -fsS "$B/services/orders" | grep -q orderId
echo "SMOKE OK"
