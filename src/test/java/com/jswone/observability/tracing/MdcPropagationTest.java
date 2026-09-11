package com.jswone.observability.tracing;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;

class MdcPropagationTest {

    @Test
    void runWithContext_appliesContextOnAnotherThreadThenRestoresIt() throws Exception {
        Map<String, String> callerContext = Map.of("traceId", "caller-trace-id");
        AtomicReference<String> observedDuringCall = new AtomicReference<>();
        AtomicReference<String> observedAfterCall = new AtomicReference<>();

        ExecutorService pooledThread = Executors.newSingleThreadExecutor();
        try {
            pooledThread
                    .submit(() -> {
                        MDC.clear(); // simulate a pooled thread with no MDC of its own
                        MdcPropagation.runWithContext(
                                callerContext, () -> observedDuringCall.set(MDC.get("traceId")));
                        observedAfterCall.set(MDC.get("traceId"));
                    })
                    .get();
        } finally {
            pooledThread.shutdown();
        }

        assertThat(observedDuringCall.get()).isEqualTo("caller-trace-id");
        assertThat(observedAfterCall.get()).isNull();
    }

    @Test
    void wrap_capturesCallingThreadContextForLaterExecutionOnAnotherThread() throws Exception {
        AtomicReference<String> observedInWrappedTask = new AtomicReference<>();
        Runnable wrapped;

        MDC.put("traceId", "capture-time-trace-id");
        try {
            wrapped = MdcPropagation.wrap(() -> observedInWrappedTask.set(MDC.get("traceId")));
        } finally {
            MDC.clear();
        }

        ExecutorService pooledThread = Executors.newSingleThreadExecutor();
        try {
            pooledThread
                    .submit(() -> {
                        MDC.clear();
                        wrapped.run();
                    })
                    .get();
        } finally {
            pooledThread.shutdown();
        }

        assertThat(observedInWrappedTask.get()).isEqualTo("capture-time-trace-id");
    }
}
