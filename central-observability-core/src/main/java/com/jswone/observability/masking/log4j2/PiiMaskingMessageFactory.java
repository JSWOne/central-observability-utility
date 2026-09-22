package com.jswone.observability.masking.log4j2;

import com.jswone.observability.masking.PiiMasker;
import org.apache.logging.log4j.message.AbstractMessageFactory;
import org.apache.logging.log4j.message.Message;
import org.apache.logging.log4j.message.ParameterizedMessageFactory;
import org.apache.logging.log4j.message.SimpleMessage;

/**
 * Masks every log message as it is created, which for Log4j2 is the earliest point available.
 *
 * <p>Earlier hooks matter here. A {@code RewritePolicy} covers only the appenders nested in its
 * own {@code <Rewrite>}, and even a {@code LogEventFactory} is too late: the OpenTelemetry
 * javaagent intercepts {@code LoggerConfig.log(..., Message, ...)}, taking the {@link Message}
 * before any {@code LogEvent} exists. Masking the message itself is therefore the only hook that
 * also redacts what the agent exports over OTLP.
 *
 * <p>Enable with a JVM argument (Log4j2 resolves it while the logger context starts, so it
 * cannot be set from XML):
 *
 * <pre>{@code
 * -Dlog4j2.messageFactory=com.jswone.observability.masking.log4j2.PiiMaskingMessageFactory
 * }</pre>
 *
 * <p>With this active the {@code <Rewrite>} wiring is redundant; point appenders straight at the
 * console.
 *
 * <p>Known gap: a caller that builds its own {@link Message} and passes it to
 * {@code logger.info(Message)} bypasses every message factory. Those statements are masked for
 * ordinary appenders only if the {@code <Rewrite>} is also configured, and cannot be masked for
 * the agent's exporter at all — a service that logs pre-built messages containing PII has to
 * turn the agent's log appender off
 * ({@code OTEL_INSTRUMENTATION_LOG4J_APPENDER_ENABLED=false}) and route OTLP logs through the
 * rewrite instead.
 */
public class PiiMaskingMessageFactory extends AbstractMessageFactory {

    private static final long serialVersionUID = 1L;

    private final ParameterizedMessageFactory delegate = ParameterizedMessageFactory.INSTANCE;

    @Override
    public Message newMessage(String message, Object... params) {
        return mask(delegate.newMessage(message, params));
    }

    @Override
    public Message newMessage(String message) {
        return mask(delegate.newMessage(message));
    }

    @Override
    public Message newMessage(Object message) {
        return mask(delegate.newMessage(message));
    }

    @Override
    public Message newMessage(CharSequence message) {
        return mask(delegate.newMessage(message));
    }

    private static Message mask(Message message) {
        String formatted = message.getFormattedMessage();
        String masked = PiiMasker.mask(formatted);
        // Same instance back when nothing matched, so the common case keeps its original Message
        // type and parameters rather than being flattened to a SimpleMessage.
        return masked.equals(formatted) ? message : new SimpleMessage(masked);
    }
}
