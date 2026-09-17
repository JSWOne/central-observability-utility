# central-observability-utility

Shared Maven starter for JSW services on **Java 21 / Spring Boot 3.x (`jakarta.*`)**. Provides
common OpenTelemetry/MDC trace correlation, async-context propagation, PII-masking log
converters (Logback + Log4j2), a generic request/response logging aspect, and structured
business-error event logging — so each service stops hand-rolling its own copy.

> **Compatibility:** requires Spring Boot 3.x and `jakarta.servlet`. Services still on Spring
> Boot 2.x / Java 11 (`javax.servlet`) — e.g. `jsw_cart_service` at time of writing — cannot
> consume this artifact until they upgrade.

## Adding the dependency

Published to the JSW GCP Artifact Registry (see `distributionManagement` in `pom.xml`).

```xml
<dependency>
    <groupId>com.jswone.observability</groupId>
    <artifactId>central-observability-utility</artifactId>
    <version>${central-observability-utility.version}</version>
</dependency>
```

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
emails, GSTIN, PAN, card/Aadhaar-shaped digit runs, and Indian mobile numbers — applied at the
appender layer so no call site needs to remember to mask.

**Logback:**

```xml
<conversionRule conversionWord="maskedMsg"
                converterClass="com.jswone.observability.masking.logback.PiiMaskingMessageConverter"/>
<appender name="CONSOLE" class="ch.qos.logback.core.ConsoleAppender">
    <encoder>
        <pattern>%d{yyyy-MM-dd HH:mm:ss.SSS} %-5level [%thread] %logger{36} : %maskedMsg%n%ex</pattern>
    </encoder>
</appender>
```

**Log4j2** (`log4j2-spring.xml` — masking a `logging.pattern.console` property alone isn't
possible, since `RewritePolicy` wraps an appender):

```xml
<Appenders>
    <Console name="Console" target="SYSTEM_OUT">
        <PatternLayout pattern="%d{yyyy-MM-dd HH:mm:ss} [%t] %-5level %logger{36} - %msg%n"/>
    </Console>
    <Rewrite name="MaskedConsole">
        <AppenderRef ref="Console"/>
        <PiiMaskingRewritePolicy/>
    </Rewrite>
</Appenders>
<Loggers>
    <Root level="INFO">
        <AppenderRef ref="MaskedConsole"/>
    </Root>
</Loggers>
```

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

## Publishing

No CI/CD is configured yet — publish manually:

```
mvn deploy
```

Uses the `artifact-registry` / `artifact-registry-snapshot` repositories declared in
`distributionManagement`, backed by the `artifactregistry-maven-wagon` extension (requires GCP
credentials configured locally, e.g. via `gcloud auth application-default login`).
