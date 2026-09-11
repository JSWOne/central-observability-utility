package com.jswone.observability;

import static org.assertj.core.api.Assertions.assertThat;

import com.jswone.observability.model.ErrorEventLog;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ErrorEventLoggerTest {

    @Test
    void logEmitsObservationWithEventAndCustomAttributes() {
        ObservationRegistry registry = ObservationRegistry.create();
        Observation.Context[] captured = new Observation.Context[1];
        registry.observationConfig().observationHandler(new ObservationHandler<Observation.Context>() {
            @Override
            public boolean supportsContext(Observation.Context context) {
                return true;
            }

            @Override
            public void onStop(Observation.Context context) {
                captured[0] = context;
            }
        });

        ErrorEventLogger logger = new ErrorEventLogger(registry);
        logger.log(ErrorEventLog.builder()
                .eventName("add_to_cart_failure")
                .system("CCP")
                .failureReason("out of stock")
                .attributes(Map.of("productId", "P123"))
                .build());

        assertThat(captured[0]).isNotNull();
        assertThat(captured[0].getName()).isEqualTo("business.error.event");
        assertThat(captured[0].getAllKeyValues())
                .anyMatch(kv -> kv.getKey().equals("event.name") && kv.getValue().equals("add_to_cart_failure"))
                .anyMatch(kv -> kv.getKey().equals("failure.reason") && kv.getValue().equals("out of stock"))
                .anyMatch(kv -> kv.getKey().equals("productId") && kv.getValue().equals("P123"))
                .anyMatch(kv -> kv.getKey().equals("log.id"));
    }
}
