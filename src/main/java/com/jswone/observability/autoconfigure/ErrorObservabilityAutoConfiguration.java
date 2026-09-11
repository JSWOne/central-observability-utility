package com.jswone.observability.autoconfigure;

import com.jswone.observability.ErrorEventLogger;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
public class ErrorObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ErrorEventLogger errorEventLogger(ObservationRegistry observationRegistry) {
        return new ErrorEventLogger(observationRegistry);
    }
}
