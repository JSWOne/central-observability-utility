package com.jswone.observability.tracing;

import org.springframework.core.task.TaskDecorator;

/**
 * Drop-in {@link TaskDecorator} for {@code ThreadPoolTaskExecutor#setTaskDecorator(...)} so
 * tasks submitted to an executor carry the submitting thread's MDC (trace id, span id, etc.).
 * Thin wrapper over {@link MdcPropagation}.
 */
public class MdcTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        return MdcPropagation.wrap(runnable);
    }
}
