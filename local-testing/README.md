# local-testing

A throwaway Docker stack that exercises every feature of the starters end to end: traces,
metrics, logs, log↔trace correlation, PII masking, and the request/response aspect — on **both**
the Spring Boot 3 and Spring Boot 2.6 lines at once.

Nothing here is published. The demo services are not modules of the library build; they depend
on the starters from your local Maven repository exactly as a real service depends on them from
Artifact Registry.

## What runs

| Container | Port | Role |
|---|---|---|
| `otel-collector` | 4317/4318, 8889 | receives all OTLP, fans out to the three backends |
| `tempo` | 3200 | traces |
| `loki` | 3100 | logs |
| `prometheus` | 9090 | metrics (scrapes the collector) |
| `grafana` | 3000 | dashboard, Explore, log→trace links |
| `demo-boot3` | 8080 | Boot 3.4 / Java 21 / jakarta / Logback, uses `central-observability-utility` |
| `demo-boot2` | 8081 | Boot 2.6 / Java 11 / javax / Log4j2, uses `central-observability-spring-boot2` |

**The collector is the swap point for TraceX/LogX.** The demo services emit plain OTLP and know
nothing about the backends. To point this at the real JSW backends instead, change the exporters
in `collector/otel-collector.yaml` — the services and the library stay untouched. That mirrors
production, where the exporter endpoint is a deploy-time environment variable.

Both demos run the OTEL javaagent from `/opt/opentelemetry-javaagent.jar`, the same path the
private `otel-java21` / `otel-java11` base images use. The public agent release is downloaded at
image build time so this harness needs no GCP credentials.

## Run it

```bash
./build.sh          # installs the library, packages both demos, builds both images
docker compose up -d
./smoke.sh          # drives every endpoint on both services
```

`build.sh` needs a JDK 21 and Maven on the host. The Boot 2.6 demo compiles with
`--release 11` under the same JDK, so no second JDK is needed.

Then open **http://localhost:3000/d/obs-utility** (Grafana, anonymous admin, no login).

Tear down with `docker compose down -v`.

## Endpoints, and what each one proves

Both services expose the same routes (`:8080` = Boot 3, `:8081` = Boot 2).

| Endpoint | Proves |
|---|---|
| `/demo/hello?email=…` | aspect `[REQUEST SUCCESS]` block; arguments and result masked |
| `/demo/pii` | every `PiiMasker` pattern: email, PAN, GSTIN, mobile, card, Aadhaar, JWT |
| `/demo/slow` | `Slow method=… durationMs=…` WARN above `slow-call-threshold-ms` |
| `/demo/fail` | aspect `[REQUEST FAILED]` at ERROR **and** a structured business error event |
| `/demo/async` | `MdcTaskDecorator` — the async log line carries the caller's `traceId` |

## Where to look

```bash
# Masking and MDC keys on the console
docker compose logs demo-boot3 demo-boot2 | grep -E 'REQUEST|BUSINESS|Sensitive'

# Raw metric names as the collector exposes them
curl -s localhost:8889/metrics | grep business_error_event
```

In Grafana:

- **Dashboard** `central-observability-utility` — request rate, p95 latency, business error
  events, JVM heap, and a logs panel.
- **Explore → Tempo → Search** — traces from both services.
- **Explore → Loki** — `{service_name="demo-boot3"}`. Each line carries `trace_id`/`span_id`;
  click the **TraceID** derived field to jump straight into that trace in Tempo.

## Known gaps this harness exposes

**1. Masking must be wired at event level, or OTLP-exported logs leak.** This harness is how
that was found, and it is worth re-running as a regression check.

The demos use the event-level hooks: `PiiMaskingTurboFilter` in `demo-boot3`'s
`logback-spring.xml`, and `-Dlog4j2.messageFactory=…PiiMaskingMessageFactory` in `demo-boot2`'s
Dockerfile. With those, console and export agree. Verify:

```bash
curl -s localhost:8080/demo/pii >/dev/null && curl -s localhost:8081/demo/pii >/dev/null && sleep 10
# must print 0
curl -s -G 'localhost:3100/loki/api/v1/query_range' \
  --data-urlencode '{service_name=~"demo-boot.*"} |~ "john.doe@gmail.com|ABCDE1234F|4111111111111111"' \
  --data-urlencode "start=$(( $(date +%s) - 120 ))000000000" \
  --data-urlencode "end=$(date +%s)000000000" | grep -c john.doe
```

Swap either demo back to the older console-only wiring (`%maskedMsg`, or the Log4j2
`<Rewrite>`) and that query starts returning full emails, PANs, cards and JWTs while the console
still looks clean — the javaagent's log appender takes the message before a pattern converter or
a rewrite policy ever sees it.

**2. Business error events produce metrics on Boot 3 only.** On Boot 3, Boot's own
`ObservationAutoConfiguration` attaches a meter handler and the demo's `micrometer-registry-otlp`
pushes `business_error_event_milliseconds_count` to the collector. Boot 2.6 ships Micrometer
1.8 and has no OTLP registry (that needs 1.12+), so `demo-boot2` registers an
`ObservationHandler` that logs the event instead — see `ErrorEventLoggingHandler`. The Boot 2
starter attaches any `ObservationHandler` bean to the registry it contributes, so that is all a
service needs to do.

**3. `demo-boot2` metrics come from the javaagent only** (HTTP server, JVM). That is enough for
the dashboard, but a Boot 2 service wanting Micrometer meters in OTLP has to bump
`micrometer.version` past 1.12 itself.
