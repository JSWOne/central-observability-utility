package com.jswone.observability;

import com.jswone.observability.model.ErrorEventLog;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import java.util.UUID;
import lombok.RequiredArgsConstructor;

/**
 * Emits a business error event as a Micrometer Observation, so any bridge
 * registered on the ObservationRegistry (e.g. micrometer-tracing-bridge-otel)
 * exports it as an OTEL log/span with these attributes.
 */
@RequiredArgsConstructor
public class ErrorEventLogger {

    private static final String OBSERVATION_NAME = "business.error.event";

    private final ObservationRegistry observationRegistry;

    public void log(ErrorEventLog event) {
        String logId = event.getLogId() != null ? event.getLogId() : UUID.randomUUID().toString();

        Observation observation = Observation.createNotStarted(OBSERVATION_NAME, observationRegistry)
                .lowCardinalityKeyValue("event.name", event.getEventName())
                .lowCardinalityKeyValue("event.system", event.getSystem())
                .highCardinalityKeyValue("log.id", logId)
                .highCardinalityKeyValue("event.time", event.getTime().toString())
                .highCardinalityKeyValue("failure.reason", nullToEmpty(event.getFailureReason()));

        if (event.getPayload() != null) {
            observation.highCardinalityKeyValue("payload", event.getPayload());
        }
        event.getAttributes().forEach((key, value) ->
                observation.highCardinalityKeyValue(key, value == null ? "" : value.toString()));

        observation.observe(() -> {});
    }

    private static String nullToEmpty(String value) {
        return value == null ? "" : value;
    }
}
