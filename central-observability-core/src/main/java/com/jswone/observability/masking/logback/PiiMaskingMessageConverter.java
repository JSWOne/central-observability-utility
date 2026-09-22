package com.jswone.observability.masking.logback;

import ch.qos.logback.classic.pattern.MessageConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.jswone.observability.masking.PiiMasker;

/**
 * Register as a {@code conversionRule} (conventionally the {@code %maskedMsg} conversion word)
 * so the console appender redacts PII from every message via {@link PiiMasker}. Example wiring
 * in {@code logback-spring.xml}:
 *
 * <pre>{@code
 * <conversionRule conversionWord="maskedMsg"
 *                 converterClass="com.jswone.observability.masking.logback.PiiMaskingMessageConverter"/>
 * }</pre>
 */
public class PiiMaskingMessageConverter extends MessageConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return PiiMasker.mask(super.convert(event));
    }
}
