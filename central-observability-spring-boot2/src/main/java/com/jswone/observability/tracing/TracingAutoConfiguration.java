package com.jswone.observability.tracing;

import javax.servlet.Filter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@ConditionalOnClass(Filter.class)
@EnableConfigurationProperties(TracingProperties.class)
public class TracingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(TraceContextMdcFilter.class)
    @ConditionalOnProperty(prefix = "jsw.observability.tracing", name = "enabled", havingValue = "true")
    public FilterRegistrationBean<TraceContextMdcFilter> traceContextMdcFilter(TracingProperties properties) {
        TraceContextMdcFilter filter = new TraceContextMdcFilter(
                properties.getTraceHeaderName(),
                properties.getTraceIdMdcKey(),
                properties.getSpanIdMdcKey(),
                properties.getRequestHeaderName(),
                properties.getRequestIdMdcKey());
        FilterRegistrationBean<TraceContextMdcFilter> registration = new FilterRegistrationBean<>(filter);
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }
}
