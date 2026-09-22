package com.jswone.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicReference;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class TraceContextMdcFilterTest {

    @RegisterExtension
    static final OpenTelemetryExtension otelExtension = OpenTelemetryExtension.create();

    private final TraceContextMdcFilter filter = new TraceContextMdcFilter("traceId", "traceId", "spanId");

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    /** Captures MDC as seen further down the chain, since the filter clears it again after doFilter returns. */
    private static FilterChain captureMdcDuringChain(AtomicReference<String> traceIdSeen, AtomicReference<String> spanIdSeen) {
        return (req, res) -> {
            traceIdSeen.set(MDC.get("traceId"));
            spanIdSeen.set(MDC.get("spanId"));
        };
    }

    @Test
    void usesRealOtelTraceContextWhenSpanIsActive() throws ServletException, IOException {
        Tracer tracer = otelExtension.getOpenTelemetry().getTracer("test");
        Span span = tracer.spanBuilder("test-span").startSpan();
        AtomicReference<String> traceIdSeen = new AtomicReference<>();
        AtomicReference<String> spanIdSeen = new AtomicReference<>();

        try (Scope scope = span.makeCurrent()) {
            MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
            MockHttpServletResponse response = new MockHttpServletResponse();

            filter.doFilter(request, response, captureMdcDuringChain(traceIdSeen, spanIdSeen));
        } finally {
            span.end();
        }

        assertThat(traceIdSeen.get()).isEqualTo(span.getSpanContext().getTraceId());
        assertThat(spanIdSeen.get()).isEqualTo(span.getSpanContext().getSpanId());
    }

    @Test
    void honorsIncomingTraceIdHeaderWhenNoActiveSpan() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
        request.addHeader("traceId", "caller-supplied-id");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdSeen = new AtomicReference<>();
        AtomicReference<String> spanIdSeen = new AtomicReference<>();

        filter.doFilter(request, response, captureMdcDuringChain(traceIdSeen, spanIdSeen));

        assertThat(traceIdSeen.get()).isEqualTo("caller-supplied-id");
    }

    @Test
    void fallsBackToRandomIdWhenNoActiveSpanOrHeader() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();
        AtomicReference<String> traceIdSeen = new AtomicReference<>();
        AtomicReference<String> spanIdSeen = new AtomicReference<>();

        filter.doFilter(request, response, captureMdcDuringChain(traceIdSeen, spanIdSeen));

        assertThat(traceIdSeen.get()).isNotBlank();
        assertThat(spanIdSeen.get()).isNotBlank();
    }

    @Test
    void clearsMdcAfterRequestSoStaleIdsDontLeakToNextRequestOnPooledThread() throws ServletException, IOException {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/anything");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, (req, res) -> {});

        assertThat(MDC.get("traceId")).isNull();
        assertThat(MDC.get("spanId")).isNull();
    }
}
