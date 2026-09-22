package com.jswone.observability.masking.logback;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import ch.qos.logback.classic.turbo.TurboFilter;
import ch.qos.logback.core.spi.FilterReply;
import com.jswone.observability.masking.PiiMasker;
import org.slf4j.Marker;
import org.slf4j.helpers.MessageFormatter;

/**
 * Masks every log event before it reaches any appender.
 *
 * <p>{@link PiiMaskingMessageConverter} can only redact what a pattern layout renders, so an
 * appender that does not use that pattern still receives the raw message — including the OTLP
 * log appender the OpenTelemetry javaagent installs, which is how unmasked PII reaches the log
 * backend while the console looks clean. A turbo filter runs before the event is built, so it
 * covers every appender.
 *
 * <p>Enable in {@code logback-spring.xml}:
 *
 * <pre>{@code
 * <turboFilter class="com.jswone.observability.masking.logback.PiiMaskingTurboFilter"/>
 * }</pre>
 *
 * <p>With this active the {@code %maskedMsg} conversion rule is redundant; use plain
 * {@code %msg}. Using both masks twice, which is wasteful rather than wrong.
 *
 * <p>Logback offers no way to rewrite an event in place, so a message that needs masking is
 * denied and re-issued through the same logger. Two consequences worth knowing: a masked
 * message is formatted eagerly (an unmasked one is not, so the usual parameterised-logging
 * savings still apply to the overwhelming majority of statements), and the re-issued event
 * carries the masked text as its message with no parameter array.
 */
public class PiiMaskingTurboFilter extends TurboFilter {

    /** Stops the re-issued event from being masked, denied and re-issued forever. */
    private static final ThreadLocal<Boolean> REISSUING = ThreadLocal.withInitial(() -> Boolean.FALSE);

    @Override
    public void start() {
        // Without this, caller data (%class, %method, %line) would resolve to this filter and the
        // logback frames beneath it rather than to the code that actually logged.
        if (getContext() instanceof LoggerContext) {
            LoggerContext loggerContext = (LoggerContext) getContext();
            addFrameworkPackage(loggerContext, getClass().getName());
            addFrameworkPackage(loggerContext, TurboFilter.class.getPackage().getName());
            addFrameworkPackage(loggerContext, LoggingEvent.class.getPackage().getName());
        }
        super.start();
    }

    @Override
    public FilterReply decide(
            Marker marker, Logger logger, Level level, String format, Object[] params, Throwable throwable) {

        if (!isStarted() || format == null || Boolean.TRUE.equals(REISSUING.get())) {
            return FilterReply.NEUTRAL;
        }
        // Deliberately not logger.isEnabledFor(level): that re-enters the turbo filter chain.
        // Without this check, every suppressed DEBUG statement would still be formatted.
        if (!level.isGreaterOrEqual(logger.getEffectiveLevel())) {
            return FilterReply.NEUTRAL;
        }

        String formatted = params == null ? format : MessageFormatter.arrayFormat(format, params).getMessage();
        String masked = PiiMasker.mask(formatted);
        if (masked.equals(formatted)) {
            // Nothing to redact, which is the common case: leave the event untouched so it keeps
            // its parameter array and its lazy formatting.
            return FilterReply.NEUTRAL;
        }

        REISSUING.set(Boolean.TRUE);
        try {
            logger.log(marker, Logger.FQCN, Level.toLocationAwareLoggerInteger(level), masked, null, throwable);
        } finally {
            REISSUING.remove();
        }
        return FilterReply.DENY;
    }

    private static void addFrameworkPackage(LoggerContext context, String packageName) {
        if (!context.getFrameworkPackages().contains(packageName)) {
            context.getFrameworkPackages().add(packageName);
        }
    }
}
