package com.jswone.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class DemoAsyncWorker {

    private static final Logger log = LoggerFactory.getLogger(DemoAsyncWorker.class);

    @Async("demoExecutor")
    public void doWork() {
        // Populated by MdcTaskDecorator on the executor; null without it.
        log.info("Async work running on {}, traceId={}", Thread.currentThread().getName(), MDC.get("traceId"));
    }
}
