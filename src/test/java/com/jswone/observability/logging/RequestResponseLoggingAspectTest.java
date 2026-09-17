package com.jswone.observability.logging;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.aop.framework.ProxyFactory;

class RequestResponseLoggingAspectTest {

    interface Greeter {
        String greet(String name);

        void explode();
    }

    static class GreeterImpl implements Greeter {
        @Override
        public String greet(String name) {
            return "hello " + name;
        }

        @Override
        public void explode() {
            throw new IllegalStateException("boom");
        }
    }

    private ListAppender<ILoggingEvent> appender;
    private Logger targetLogger;

    @BeforeEach
    void attachAppender() {
        // The proxy is a JDK dynamic proxy implementing Greeter, so the aspect resolves the
        // logger from the interface method's declaring class (Greeter), not GreeterImpl.
        targetLogger = (Logger) LoggerFactory.getLogger(Greeter.class);
        targetLogger.setLevel(Level.ALL);
        appender = new ListAppender<>();
        appender.setContext((LoggerContext) LoggerFactory.getILoggerFactory());
        appender.start();
        targetLogger.addAppender(appender);
    }

    @AfterEach
    void detachAppender() {
        targetLogger.detachAppender(appender);
    }

    private Greeter proxyWith(RequestResponseLoggingAspect advice) {
        ProxyFactory factory = new ProxyFactory(new GreeterImpl());
        factory.addAdvice(advice);
        return (Greeter) factory.getProxy();
    }

    @Test
    void logsSuccessAtConfiguredLevelWithMaskedArgsAndResult() {
        Greeter greeter = proxyWith(new RequestResponseLoggingAspect(2000, "INFO", 2000));

        greeter.greet("john14@gmail.com");

        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size());
        assertEquals(Level.INFO, events.get(0).getLevel());
        String rendered = events.get(0).getFormattedMessage();
        assertTrue(rendered.contains("[REQUEST SUCCESS]"), rendered);
        assertFalse(rendered.contains("john14@gmail.com"), rendered);
        assertTrue(rendered.contains("j***@gmail.com"), rendered);
    }

    @Test
    void staysSilentBelowConfiguredLevel() {
        Greeter greeter = proxyWith(new RequestResponseLoggingAspect(2000, "DEBUG", 2000));
        targetLogger.setLevel(Level.INFO);

        greeter.greet("world");

        assertEquals(0, appender.list.size());
    }

    @Test
    void logsFailureAtErrorRegardlessOfSuccessLevel() {
        Greeter greeter = proxyWith(new RequestResponseLoggingAspect(2000, "DEBUG", 2000));
        targetLogger.setLevel(Level.INFO);

        assertThrows(IllegalStateException.class, greeter::explode);

        List<ILoggingEvent> events = appender.list;
        assertEquals(1, events.size());
        assertEquals(Level.ERROR, events.get(0).getLevel());
        assertTrue(events.get(0).getFormattedMessage().contains("[REQUEST FAILED]"));
    }
}
