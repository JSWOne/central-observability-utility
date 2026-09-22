package com.jswone.observability.masking.log4j2;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.logging.log4j.message.Message;
import org.junit.jupiter.api.Test;

class PiiMaskingMessageFactoryTest {

    private final PiiMaskingMessageFactory factory = new PiiMaskingMessageFactory();

    /** The whole point: the Message itself is masked, so every appender and the javaagent's
     * OTLP exporter all see redacted text. */
    @Test
    void masksPiiSuppliedAsAParameter() {
        Message message = factory.newMessage("contacting {}", "john.doe@gmail.com");

        assertThat(message.getFormattedMessage()).isEqualTo("contacting j***@gmail.com");
    }

    @Test
    void masksPiiComingFromTheFormatItself() {
        Message message = factory.newMessage("card 4111111111111111 declined");

        assertThat(message.getFormattedMessage()).isEqualTo("card 411111***1111 declined");
    }

    @Test
    void masksAcrossEveryFactoryOverload() {
        assertThat(factory.newMessage((Object) "pan ABCDE1234F").getFormattedMessage())
                .isEqualTo("pan ***");
        assertThat(factory.newMessage((CharSequence) "pan ABCDE1234F").getFormattedMessage())
                .isEqualTo("pan ***");
        assertThat(factory.newMessage("aadhaar {}", "123412341234").getFormattedMessage())
                .isEqualTo("aadhaar 123412***1234");
    }

    @Test
    void leavesCleanMessagesUntouchedSoTheyKeepTheirParameters() {
        Message message = factory.newMessage("order {} placed", "ORD-1");

        assertThat(message.getFormattedMessage()).isEqualTo("order ORD-1 placed");
        assertThat(message.getParameters()).containsExactly("ORD-1");
    }
}
