package com.jswone.observability.autoconfigure;

import static org.assertj.core.api.Assertions.assertThat;

import com.jswone.observability.ErrorEventLogger;
import com.jswone.observability.model.ErrorEventLog;
import com.jswone.observability.logging.LoggingAspectAutoConfiguration;
import com.jswone.observability.tracing.TraceContextMdcFilter;
import com.jswone.observability.tracing.TracingAutoConfiguration;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.observation.ObservationRegistry;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.io.support.SpringFactoriesLoader;

/**
 * Spring Boot 2.6 discovers autoconfiguration through META-INF/spring.factories, not the
 * AutoConfiguration.imports file the Boot 3 starter uses, so that wiring is asserted directly
 * here — a typo in the factories file would otherwise fail silently, with no beans registered.
 */
class AutoConfigurationRegistrationTest {

    @Test
    void springFactoriesRegistersEveryAutoConfiguration() {
        List<String> registered = SpringFactoriesLoader.loadFactoryNames(
                EnableAutoConfiguration.class, getClass().getClassLoader());

        assertThat(registered)
                .contains(
                        ErrorObservabilityAutoConfiguration.class.getName(),
                        TracingAutoConfiguration.class.getName(),
                        LoggingAspectAutoConfiguration.class.getName());
    }

    @Test
    void contributesErrorEventLoggerAndItsRegistryBecauseBoot2AutoConfiguresNeither() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ErrorObservabilityAutoConfiguration.class))
                .run(context -> assertThat(context)
                        .hasSingleBean(ErrorEventLogger.class)
                        .hasSingleBean(ObservationRegistry.class));
    }

    @Test
    void attachesObservationHandlerBeansToTheRegistryItContributes() {
        new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(ErrorObservabilityAutoConfiguration.class))
                .withUserConfiguration(RecordingHandlerConfiguration.class)
                .run(context -> {
                    RecordingHandler handler = context.getBean(RecordingHandler.class);
                    context.getBean(ErrorEventLogger.class)
                            .log(ErrorEventLog.builder().eventName("boom").system("test").build());

                    assertThat(handler.stopped).isTrue();
                });
    }

    @Configuration(proxyBeanMethods = false)
    static class RecordingHandlerConfiguration {

        @Bean
        RecordingHandler recordingHandler() {
            return new RecordingHandler();
        }
    }

    static class RecordingHandler implements ObservationHandler<Observation.Context> {

        boolean stopped;

        @Override
        public boolean supportsContext(Observation.Context context) {
            return true;
        }

        @Override
        public void onStop(Observation.Context context) {
            stopped = true;
        }
    }

    @Test
    void registersTraceFilterOnlyWhenTracingIsEnabled() {
        ApplicationContextRunner runner = new ApplicationContextRunner()
                .withConfiguration(AutoConfigurations.of(TracingAutoConfiguration.class));

        runner.run(context -> assertThat(context).doesNotHaveBean(FilterRegistrationBean.class));

        runner.withPropertyValues("jsw.observability.tracing.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(FilterRegistrationBean.class);
                    assertThat(context.getBean(FilterRegistrationBean.class).getFilter())
                            .isInstanceOf(TraceContextMdcFilter.class);
                });
    }
}
