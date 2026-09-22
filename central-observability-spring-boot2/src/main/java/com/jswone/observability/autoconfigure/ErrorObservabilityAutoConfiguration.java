package com.jswone.observability.autoconfigure;

import com.jswone.observability.ErrorEventLogger;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Spring Boot 2 counterpart of the Boot 3 autoconfiguration.
 *
 * <p>Boot 2.6 ships Micrometer 1.8, which predates {@code micrometer-observation} and
 * auto-configures no {@link ObservationRegistry}, so this starter brings the artifact and
 * contributes the registry itself. Any {@link ObservationHandler} beans in the context are
 * attached to it, mirroring what Boot 3's own {@code ObservationAutoConfiguration} does — a
 * registry with no handler drops every observation silently, so without this a service's
 * handler bean would look wired up and do nothing. Services that define their own registry bean
 * keep it, handlers included.
 */
@Configuration(proxyBeanMethods = false)
public class ErrorObservabilityAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ObservationRegistry observationRegistry(ObjectProvider<ObservationHandler<?>> handlers) {
        ObservationRegistry registry = ObservationRegistry.create();
        handlers.orderedStream().forEach(registry.observationConfig()::observationHandler);
        return registry;
    }

    @Bean
    @ConditionalOnMissingBean
    public ErrorEventLogger errorEventLogger(ObservationRegistry observationRegistry) {
        return new ErrorEventLogger(observationRegistry);
    }
}
