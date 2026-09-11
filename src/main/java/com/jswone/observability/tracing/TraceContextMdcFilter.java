package com.jswone.observability.tracing;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Puts the real OTEL trace/span id (from the javaagent-created span) into MDC for every
 * request, so log lines correlate with the same trace id shown in TraceX/LogX. Falls back to
 * a caller-supplied header or a random id only when no OTEL span is active.
 */
@RequiredArgsConstructor
public class TraceContextMdcFilter extends OncePerRequestFilter {

    private final String traceHeaderName;
    private final String traceIdMdcKey;
    private final String spanIdMdcKey;

    @Override
    protected void doFilterInternal(
            HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        SpanContext spanContext = Span.current().getSpanContext();
        String traceId;
        String spanId;
        if (spanContext.isValid()) {
            traceId = spanContext.getTraceId();
            spanId = spanContext.getSpanId();
        } else {
            String randomId = UUID.randomUUID().toString();
            String headerTraceId = request.getHeader(traceHeaderName);
            traceId = (headerTraceId == null || headerTraceId.isEmpty()) ? randomId : headerTraceId;
            spanId = randomId;
        }

        MDC.put(traceIdMdcKey, traceId);
        MDC.put(spanIdMdcKey, spanId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            // Tomcat reuses worker threads across requests, so stale ids must not survive this one.
            MDC.remove(traceIdMdcKey);
            MDC.remove(spanIdMdcKey);
        }
    }
}
