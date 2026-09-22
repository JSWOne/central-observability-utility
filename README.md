# central-observability-utility

Shared Maven starters for JSW services. Provides common OpenTelemetry/MDC trace correlation,
async-context propagation, PII-masking log converters (Logback + Log4j2), a generic
request/response logging aspect, and structured business-error event logging — so each service
stops hand-rolling its own copy.

## Modules

| Module | For | Java | Discovery |
|---|---|---|---|
| `central-observability-core` | shared internals, not consumed directly | 11 | — |
| `central-observability-utility` | Spring Boot 3.x (`jakarta.servlet`) | 21 | `AutoConfiguration.imports` |
| `central-observability-spring-boot2` | Spring Boot 2.6+ (`javax.servlet`) | 11 | `META-INF/spring.factories` |

Both starters depend on `opentelemetry-api` at compile scope. The javaagent does not supply it:
it shades its own copy and bridges calls to the one the application carries, so without it
`TraceContextMdcFilter` fails every request with `ClassNotFoundException`.

Both starters expose the same classes under the same package names and the same
`jsw.observability.*` properties, so service code and configuration are identical on either
line. They are alternatives — never put both on one classpath.

`central-observability-core` holds everything that is servlet-API-free: `PiiMasker` and both log
converters, `MdcPropagation`, `MdcTaskDecorator`, `ErrorEventLog`/`ErrorEventLogger`,
`RequestResponseLoggingAspect`, and the two `@ConfigurationProperties` classes. It is compiled
against the oldest supported dependency versions and released for Java 11, so the same bytecode
runs on both lines. Only four classes are duplicated per starter — the trace filter and the
three configuration classes — because `javax`/`jakarta` and
`@Configuration`+`spring.factories`/`@AutoConfiguration`+`.imports` cannot be expressed once.
Keep the two copies in step.

## Adding the dependency

Published to the JSW GCP Artifact Registry (see `distributionManagement` in the parent `pom.xml`).

**Spring Boot 3.x / Java 21:**

```xml
<dependency>
    <groupId>com.jswone.observability</groupId>
    <artifactId>central-observability-utility</artifactId>
    <version>${central-observability-utility.version}</version>
</dependency>
```

**Spring Boot 2.6+ / Java 11** (e.g. `jsw_cart_service`):

```xml
<dependency>
    <groupId>com.jswone.observability</groupId>
    <artifactId>central-observability-spring-boot2</artifactId>
    <version>${central-observability-utility.version}</version>
</dependency>
```

Below Boot 2.6 is not supported: `@ConditionalOnProperty`-driven `FilterRegistrationBean`
registration and the properties binding are tested against 2.6 only.

## MDC key contract

| MDC key | Populated by | Notes |
|---|---|---|
| `traceId` | `TraceContextMdcFilter` | From the active OTEL span if the javaagent is attached, else a caller-supplied header or a random id. Configurable via `jsw.observability.tracing.trace-id-mdc-key`. |
| `spanId` | `TraceContextMdcFilter` | Same source as `traceId`. Configurable via `jsw.observability.tracing.span-id-mdc-key`. |
| `requestId` *(optional)* | `TraceContextMdcFilter` | Only populated when both `jsw.observability.tracing.request-header-name` and `jsw.observability.tracing.request-id-mdc-key` are set. Off by default. |

Reference these keys directly in your logging pattern, e.g.:

```
%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} trace=%X{traceId} span=%X{spanId} : %msg%n
```

## Trace/span correlation filter

Opt-in (off by default — registering it changes MDC contents for every request):

```properties
jsw.observability.tracing.enabled=true
# Optional overrides (defaults shown):
jsw.observability.tracing.trace-header-name=traceId
jsw.observability.tracing.trace-id-mdc-key=traceId
jsw.observability.tracing.span-id-mdc-key=spanId
# Optional, off unless both are set:
jsw.observability.tracing.request-header-name=X-Request-ID
jsw.observability.tracing.request-id-mdc-key=requestId
```

## Async MDC propagation

For code that hands work to an executor, gRPC callback, or any thread with no MDC of its own:

```java
// Manual wrap/run:
Runnable task = MdcPropagation.wrap(() -> doWork());

// Or wire once into a ThreadPoolTaskExecutor:
executor.setTaskDecorator(new MdcTaskDecorator());
```

## PII masking

`PiiMasker` (in `com.jswone.observability.masking`) redacts JWTs, bearer/basic credentials,
emails, GSTIN, PAN, card/Aadhaar-shaped digit runs, and Indian mobile numbers.

Mask at **event level**. Both hooks below redact the log event itself, before it reaches any
appender — including the OTLP log appender the OpenTelemetry javaagent installs. That last part
is the reason to prefer them: the older pattern/rewrite wiring leaves the console clean while
the record exported to LogX still carries the raw PAN, card and JWT.

**Logback** — register the turbo filter and use a plain `%msg`:

```xml
<configuration>
    <turboFilter class="com.jswone.observability.masking.logback.PiiMaskingTurboFilter"/>

    <appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
        <encoder>
            <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} trace=%X{traceId} span=%X{spanId} : %msg%n%ex</pattern>
        </encoder>
    </appender>

    <root level="INFO">
        <appender-ref ref="CONSOLE"/>
    </root>
</configuration>
```

Logback cannot rewrite an event in place, so a message that needs masking is denied and
re-issued through the same logger. Messages with nothing to redact are untouched and keep their
lazy formatting, which is almost all of them.

**Log4j2** — a JVM argument, because Log4j2 resolves the message factory before it reads any
configuration file:

```
-Dlog4j2.messageFactory=com.jswone.observability.masking.log4j2.PiiMaskingMessageFactory
```

Masking at `Message` creation is the only Log4j2 hook early enough: the javaagent intercepts
`LoggerConfig.log(..., Message, ...)`, which runs before any `LogEvent` exists, so even a
`LogEventFactory` is too late.

### The older console-only wiring

`PiiMaskingMessageConverter` (the `%maskedMsg` conversion word) and `PiiMaskingRewritePolicy`
(the `<Rewrite>` appender) still work and are unchanged. They mask **only what passes through
that pattern or that rewrite** — anything else, notably the javaagent's OTLP log export, gets
the raw message. Use them only where no OTLP log exporter is attached. Running one of them
alongside an event-level hook masks twice: wasteful, not wrong.

One residual gap on Log4j2: a caller that builds its own `Message` and calls
`logger.info(Message)` bypasses every message factory. A service that does that with PII has to
turn off the agent's log appender (`OTEL_INSTRUMENTATION_LOG4J_APPENDER_ENABLED=false`) and
route OTLP logs through the `<Rewrite>` instead.

## Request/response logging aspect

Opt-in, replaces a hand-rolled `@Aspect`:

```properties
jsw.observability.logging.aspect.enabled=true
jsw.observability.logging.aspect.base-packages=com.foo.controller,com.foo.service
jsw.observability.logging.aspect.slow-call-threshold-ms=2000
# DEBUG by default; set to INFO to match always-on request/response logging:
jsw.observability.logging.aspect.success-log-level=INFO
jsw.observability.logging.aspect.max-rendered-length=2000
```

Logs a `[REQUEST SUCCESS]` block (with masked/truncated args and result) at
`success-log-level`, a `[REQUEST FAILED]` block at ERROR with the stack trace on any exception,
and a `Slow method=... durationMs=...` WARN whenever a call meets `slow-call-threshold-ms`
regardless of `success-log-level`.

## Business error events

See `ErrorEventLogger` / `ErrorObservabilityAutoConfiguration` — emits structured business
errors as Micrometer Observations, exported as OTEL logs/span events by whatever
`micrometer-tracing-bridge-otel` (or equivalent) the consumer has on its classpath.

**On Spring Boot 2.6 this needs one extra step.** Boot 2.6 ships Micrometer 1.8, which predates
`micrometer-observation` and auto-configures no `ObservationRegistry`, so the Boot 2 starter
brings the artifact itself and contributes a plain `ObservationRegistry.create()`. That registry
has no `ObservationHandler` attached, so error events are recorded but exported nowhere until
the service registers a handler — or its own `ObservationRegistry` bean, which the starter then
backs off from. On Boot 3 the registry and its handlers come from Boot's own autoconfiguration
and nothing extra is needed.

## OpenTelemetry javaagent (Dockerfile convention)

This library does not attach or configure the OTEL javaagent — that happens at the container
base-image layer, outside what a Maven dependency can provide. The convention across JSW
services:

```dockerfile
FROM asia-south1-docker.pkg.dev/modular-bucksaw-305821/jopl/otel-java21:latest
# ...
ENV OTEL_SERVICE_NAME=<your-service-name>
ENTRYPOINT ["java", "-javaagent:/opt/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
```

The `otel-java*` base image ships the javaagent at `/opt/opentelemetry-javaagent.jar`. Set
`OTEL_SERVICE_NAME` to the service's own name so spans are attributed correctly. Additional
exporter configuration (`OTEL_EXPORTER_OTLP_ENDPOINT`, etc.) is supplied at deploy time via
environment variables, not baked into the image.

## Local testing

`local-testing/` is a Docker stack — OTEL Collector, Tempo, Loki, Prometheus, Grafana, and one
demo service per starter — that exercises traces, metrics, logs, log/trace correlation, masking
and the aspect on both Boot lines at once:

```bash
cd local-testing && ./build.sh && docker compose up -d && ./smoke.sh
```

See `local-testing/README.md`, including the gaps it exposes — most importantly that logs
exported over OTLP bypass `PiiMasker` entirely.

## Publishing

No CI/CD is configured yet — publish manually:

```
mvn deploy
```

Deploys all three modules. `mvn test` builds every module; `mvn -pl <module> -am test` builds one.

Uses the `artifact-registry` / `artifact-registry-snapshot` repositories declared in
`distributionManagement`, backed by the `artifactregistry-maven-wagon` extension (requires GCP
credentials configured locally, e.g. via `gcloud auth application-default login`).
