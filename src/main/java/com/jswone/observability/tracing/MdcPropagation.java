package com.jswone.observability.tracing;

import java.util.Map;
import org.slf4j.MDC;

/**
 * Carries the calling thread's MDC (trace id, span id, etc.) into async callbacks that run on
 * threads with no MDC of their own — executor pools, gRPC/Gax callback threads, and similar —
 * so log lines from that callback still correlate with the request that triggered it.
 */
public final class MdcPropagation {

    private MdcPropagation() {}

    /** Capture the current thread's MDC now, to hand off to {@link #runWithContext} later on another thread. */
    public static Map<String, String> captureContext() {
        return MDC.getCopyOfContextMap();
    }

    /** Wrap a task so it runs with {@code context} applied, for APIs that accept a plain {@link Runnable}. */
    public static Runnable wrap(Runnable task) {
        Map<String, String> context = captureContext();
        return () -> runWithContext(context, task);
    }

    /**
     * Run {@code task} with {@code context} applied to MDC, restoring whatever the executing
     * thread's MDC held before (typically nothing) once {@code task} finishes.
     */
    public static void runWithContext(Map<String, String> context, Runnable task) {
        Map<String, String> previousContext = MDC.getCopyOfContextMap();
        if (context != null) {
            MDC.setContextMap(context);
        }
        try {
            task.run();
        } finally {
            if (previousContext != null) {
                MDC.setContextMap(previousContext);
            } else {
                MDC.clear();
            }
        }
    }
}
