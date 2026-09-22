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
            } else if (isEnabled(log, successLevel)) {
                logAt(
                        log,
                        successLevel,
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

    /**
     * Level-enabled check and level-dispatching log call, written the long way on purpose:
     * {@code Logger.isEnabledForLevel}/{@code Logger.atLevel} exist only in SLF4J 2.x, and this
     * class also ships to Spring Boot 2.6 services running SLF4J 1.7.
     */
    private static boolean isEnabled(Logger log, Level level) {
        switch (level) {
            case ERROR:
                return log.isErrorEnabled();
            case WARN:
                return log.isWarnEnabled();
            case INFO:
                return log.isInfoEnabled();
            case DEBUG:
                return log.isDebugEnabled();
            case TRACE:
                return log.isTraceEnabled();
            default:
                return false;
        }
    }

    private static void logAt(Logger log, Level level, String format, Object... arguments) {
        switch (level) {
            case ERROR:
                log.error(format, arguments);
                break;
            case WARN:
                log.warn(format, arguments);
                break;
            case INFO:
                log.info(format, arguments);
                break;
            case DEBUG:
                log.debug(format, arguments);
                break;
            case TRACE:
                log.trace(format, arguments);
                break;
            default:
                break;
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
