package com.jswone.observability.logging;

import com.jswone.observability.masking.PiiMasker;
import java.util.Arrays;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.slf4j.event.Level;

/**
 * Cross-cutting entry/exit/failure logging, applied to whatever packages
 * {@link LoggingAspectProperties#getBasePackages()} names. Arguments and results are masked via
 * {@link PiiMasker} and truncated before being rendered, so nothing here needs a service to
 * remember to mask its own log statements.
 *
 * <p>Wired as the advice of a {@link org.springframework.aop.support.DefaultPointcutAdvisor} in
 * {@link LoggingAspectAutoConfiguration} rather than a static {@code @Aspect}, because the
 * pointcut expression depends on runtime configuration ({@code base-packages}).
 */
final class RequestResponseLoggingAspect implements MethodInterceptor {

    private static final String TRUNCATION_SUFFIX = "...<truncated>";

    private final long slowCallThresholdMs;
    private final Level successLevel;
    private final int maxRenderedLength;

    RequestResponseLoggingAspect(long slowCallThresholdMs, String successLogLevel, int maxRenderedLength) {
        this.slowCallThresholdMs = slowCallThresholdMs;
        this.successLevel = Level.valueOf(successLogLevel.toUpperCase());
        this.maxRenderedLength = maxRenderedLength;
    }

    @Override
    public Object invoke(MethodInvocation invocation) throws Throwable {
        Logger log = LoggerFactory.getLogger(invocation.getMethod().getDeclaringClass());
        String className = invocation.getMethod().getDeclaringClass().getName();
        String methodName = invocation.getMethod().getName();
        String arguments = render(Arrays.deepToString(invocation.getArguments()));

        long startMs = System.currentTimeMillis();
        try {
            Object result = invocation.proceed();
            long durationMs = System.currentTimeMillis() - startMs;

            if (durationMs >= slowCallThresholdMs) {
                log.warn(
                        "Slow method={} durationMs={} thresholdMs={}",
                        methodName,
                        durationMs,
                        slowCallThresholdMs);
            } else if (log.isEnabledForLevel(successLevel)) {
                log.atLevel(successLevel)
                        .log(
                                "\n[REQUEST SUCCESS]\nTrace     : {}\nClass     : {}\nMethod    :"
                                        + " {}\nArguments : {}\nResult    : {}\nTimeTaken :"
                                        + " {} ms\n",
                                MDC.get("traceId"),
                                className,
                                methodName,
                                arguments,
                                render(String.valueOf(result)),
                                durationMs);
            }
            return result;
        } catch (Throwable throwable) {
            long durationMs = System.currentTimeMillis() - startMs;
            log.error(
                    "\n[REQUEST FAILED]\nTrace     : {}\nSpan      : {}\nClass     : {}\nMethod   "
                            + " : {}\nArguments : {}\nError     : {}\nTimeTaken : {} ms\n",
                    MDC.get("traceId"),
                    MDC.get("spanId"),
                    className,
                    methodName,
                    arguments,
                    throwable.getMessage(),
                    durationMs,
                    throwable);
            throw throwable;
        }
    }

    private String render(String value) {
        String masked = PiiMasker.mask(value);
        if (masked == null) {
            return "null";
        }
        return masked.length() <= maxRenderedLength
                ? masked
                : masked.substring(0, maxRenderedLength) + TRUNCATION_SUFFIX;
    }
}
