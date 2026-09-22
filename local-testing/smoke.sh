#!/usr/bin/env bash
# Drives every feature of the starter against both demo services, then points at where to look.
set -uo pipefail
cd "$(dirname "$0")"

for port_and_name in "8080 demo-boot3" "8081 demo-boot2"; do
  set -- $port_and_name
  port=$1
  name=$2
  echo "==> $name (localhost:$port)"
  echo "--- success path, masked arguments"
  curl -s "localhost:$port/demo/hello?email=john.doe@gmail.com" -H 'X-Request-ID: smoke-run'; echo
  echo "--- full PII sweep"
  curl -s "localhost:$port/demo/pii" >/dev/null && echo "sent"
  echo "--- async MDC propagation"
  curl -s "localhost:$port/demo/async"; echo
  echo "--- business error event + [REQUEST FAILED]"
  curl -s "localhost:$port/demo/fail?productId=P123" >/dev/null && echo "sent"
  echo "--- slow call WARN (takes ~2.5s)"
  curl -s "localhost:$port/demo/slow"; echo
  echo
done

cat <<'MSG'
Now look at:
  Console logs (masking)     docker compose logs demo-boot3 demo-boot2 | grep -E 'REQUEST|BUSINESS|Sensitive'
  Grafana dashboard          http://localhost:3000/d/obs-utility
  Traces                     http://localhost:3000/explore  (Tempo -> Search)
  Logs, click TraceID        http://localhost:3000/explore  (Loki -> {service_name="demo-boot3"})
  Raw metric names           http://localhost:8889/metrics
MSG
