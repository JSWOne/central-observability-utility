package com.jswone.observability.masking.log4j2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.core.LogEvent;
import org.apache.logging.log4j.core.impl.Log4jLogEvent;
import org.apache.logging.log4j.message.SimpleMessage;
import org.junit.jupiter.api.Test;

class PiiMaskingRewritePolicyTest {

    @Test
    void masksTheFormattedMessage() {
        LogEvent source =
                Log4jLogEvent.newBuilder()
                        .setLoggerName(getClass().getName())
                        .setLevel(Level.INFO)
                        .setMessage(new SimpleMessage("customer email john14@gmail.com"))
                        .build();

        PiiMaskingRewritePolicy policy = PiiMaskingRewritePolicy.createPolicy();
        LogEvent rewritten = policy.rewrite(source);

        assertEquals("customer email j***@gmail.com", rewritten.getMessage().getFormattedMessage());
    }
}
