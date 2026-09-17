package com.jswone.observability.tracing;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcTaskDecoratorTest {

    @AfterEach
    void clearMdc() {
        MDC.clear();
    }

    @Test
    void decoratedTaskSeesCallingThreadsMdc() throws InterruptedException {
        MDC.put("traceId", "trace-123");
        AtomicReference<String> seenOnOtherThread = new AtomicReference<>();

        Runnable task = () -> seenOnOtherThread.set(MDC.get("traceId"));
        Runnable decorated = new MdcTaskDecorator().decorate(task);

        Thread thread = new Thread(decorated);
        thread.start();
        thread.join();

        assertEquals("trace-123", seenOnOtherThread.get());
        // The decorating thread's own MDC is untouched by capturing it for handoff.
        assertEquals("trace-123", MDC.get("traceId"));
    }

    @Test
    void otherThreadMdcIsClearedAfterTaskRunsWithNoPriorContext() throws InterruptedException {
        MDC.put("traceId", "trace-456");
        Runnable decorated = new MdcTaskDecorator().decorate(() -> {});

        AtomicReference<String> afterRun = new AtomicReference<>();
        Thread thread =
                new Thread(
                        () -> {
                            decorated.run();
                            afterRun.set(MDC.get("traceId"));
                        });
        thread.start();
        thread.join();

        assertNull(afterRun.get());
    }
}
