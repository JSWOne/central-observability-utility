package com.jswone.demo;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * What a Spring Boot 2.6 service has to add for {@code ErrorEventLogger} to do anything
 * visible. The starter contributes a bare {@code ObservationRegistry} because Boot 2.6
 * auto-configures none, and a registry with no handler silently drops every observation. Boot 3
 * services need none of this — Boot wires the metrics and tracing handlers itself.
 */
@Component
public class ErrorEventLoggingHandler implements ObservationHandler<Observation.Context> {

    private static final Logger log = LoggerFactory.getLogger(ErrorEventLoggingHandler.class);

    @Override
    public boolean supportsContext(Observation.Context context) {
        return true;
    }

    @Override
    public void onStop(Observation.Context context) {
        log.info("[BUSINESS ERROR EVENT] name={} keyValues={}", context.getName(), context.getAllKeyValues());
    }
}
