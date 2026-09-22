package com.jswone.observability.masking.log4j2;

import com.jswone.observability.masking.PiiMasker;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.appender.rewrite.RewritePolicy;
import org.apache.logging.log4j.core.config.plugins.Plugin;
import org.apache.logging.log4j.core.config.plugins.PluginFactory;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;

/**
 * Log4j2-native equivalent of the Logback {@code PiiMaskingMessageConverter}: rewrites every
 * event's formatted message through {@link PiiMasker} before it reaches the appender. Wire it
 * into a {@code <Rewrite>} appender wrapping the real Console appender:
 *
 * <pre>{@code
 * <Rewrite name="MaskedConsole">
 *     <AppenderRef ref="Console"/>
 *     <PiiMaskingRewritePolicy/>
 * </Rewrite>
 * }</pre>
 */
@Plugin(
        name = "PiiMaskingRewritePolicy",
        category = "Core",
        elementType = "rewritePolicy",
        printObject = true)
public final class PiiMaskingRewritePolicy implements RewritePolicy {

    @Override
    public LogEvent rewrite(LogEvent source) {
        String masked = PiiMasker.mask(source.getMessage().getFormattedMessage());
        return new Log4jLogEvent.Builder(source).setMessage(new SimpleMessage(masked)).build();
    }

    @PluginFactory
    public static PiiMaskingRewritePolicy createPolicy() {
        return new PiiMaskingRewritePolicy();
    }
}
