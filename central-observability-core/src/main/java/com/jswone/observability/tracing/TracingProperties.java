package com.jswone.observability.tracing;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "jsw.observability.tracing")
public class TracingProperties {

    /**
     * Off by default: registering the filter changes MDC contents for every request,
     * so each service opts in explicitly.
     */
    private boolean enabled = false;

    /** Incoming request header carrying a caller-supplied trace id, used only when no OTEL span is active. */
    private String traceHeaderName = "traceId";

    private String traceIdMdcKey = "traceId";

    private String spanIdMdcKey = "spanId";

    /**
     * Incoming request header carrying a caller-supplied request id. Null (the default) disables
     * request-id tracking entirely, so services that don't want a third MDC key see no behavior
     * change. Set both this and {@link #requestIdMdcKey} to opt in.
     */
    private String requestHeaderName;

    /** MDC key the resolved request id is stored under. Null disables request-id tracking. */
    private String requestIdMdcKey;
}
