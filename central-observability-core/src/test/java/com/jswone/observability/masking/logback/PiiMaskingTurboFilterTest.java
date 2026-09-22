package com.jswone.observability.masking.logback;

import static org.assertj.core.api.Assertions.assertThat;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class PiiMaskingTurboFilterTest {

    private LoggerContext context;
    private Logger logger;
    private ListAppender<ILoggingEvent> appender;

    @BeforeEach
    void setUp() {
        context = new LoggerContext();
        appender = new ListAppender<>();
        appender.setContext(context);
        appender.start();

        PiiMaskingTurboFilter filter = new PiiMaskingTurboFilter();
        filter.setContext(context);
        filter.start();
        context.addTurboFilter(filter);

        logger = context.getLogger("test");
        logger.addAppender(appender);
        logger.setLevel(Level.INFO);
    }

    /** The whole point: what the appender receives is masked, not just what a pattern renders. */
    @Test
    void appenderReceivesMaskedMessage() {
        logger.info("contacting {}", "john.doe@gmail.com");

        assertThat(appender.list).hasSize(1);
        assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("contacting j***@gmail.com");
    }

    @Test
    void masksPiiComingFromTheFormatItselfNotOnlyFromParameters() {
        logger.info("card 4111111111111111 declined");

        assertThat(appender.list.get(0).getFormattedMessage()).isEqualTo("card 411111***1111 declined");
    }

    @Test
    void leavesCleanEventsUntouchedSoTheyKeepLazyFormatting() {
        logger.info("order {} placed", "ORD-1");

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getFormattedMessage()).isEqualTo("order ORD-1 placed");
        assertThat(event.getArgumentArray()).containsExactly("ORD-1");
    }

    @Test
    void preservesLevelLoggerNameAndThrowableOnTheReissuedEvent() {
        RuntimeException failure = new RuntimeException("boom");

        logger.warn("failed for john.doe@gmail.com", failure);

        ILoggingEvent event = appender.list.get(0);
        assertThat(event.getLevel()).isEqualTo(Level.WARN);
        assertThat(event.getLoggerName()).isEqualTo("test");
        assertThat(event.getFormattedMessage()).isEqualTo("failed for j***@gmail.com");
        assertThat(event.getThrowableProxy().getMessage()).isEqualTo("boom");
    }

    /** A message that still matches after masking must not be denied and re-issued forever. */
    @Test
    void reissuesEachMaskedEventExactlyOnce() {
        logger.info("a@b.co and c@d.co and 4111111111111111");

        assertThat(appender.list).hasSize(1);
    }

    @Test
    void doesNotEmitEventsBelowTheLoggerLevel() {
        logger.debug("suppressed john.doe@gmail.com");

        assertThat(appender.list).isEmpty();
    }
}
