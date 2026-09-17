package com.jswone.observability.masking.logback;

import static org.junit.jupiter.api.Assertions.assertEquals;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.LoggingEvent;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;

class PiiMaskingMessageConverterTest {

    @Test
    void masksTheRenderedMessage() {
        LoggerContext context = new LoggerContext();
        Logger logger = context.getLogger(PiiMaskingMessageConverterTest.class);
        LoggingEvent event =
                new LoggingEvent(
                        getClass().getName(),
                        (ch.qos.logback.classic.Logger) logger,
                        Level.INFO,
                        "customer email john14@gmail.com",
                        null,
                        null);

        PiiMaskingMessageConverter converter = new PiiMaskingMessageConverter();

        assertEquals("customer email j***@gmail.com", converter.convert(event));
    }
}
