package com.jswone.observability.logging;

import java.util.stream.Collectors;
import org.aopalliance.intercept.MethodInterceptor;
import org.springframework.aop.Advisor;
import org.springframework.aop.aspectj.AspectJExpressionPointcut;
import org.springframework.aop.support.DefaultPointcutAdvisor;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

@AutoConfiguration
@ConditionalOnClass(AspectJExpressionPointcut.class)
@EnableConfigurationProperties(LoggingAspectProperties.class)
public class LoggingAspectAutoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "jsw.observability.logging.aspect", name = "enabled", havingValue = "true")
    public Advisor requestResponseLoggingAdvisor(LoggingAspectProperties properties) {
        AspectJExpressionPointcut pointcut = new AspectJExpressionPointcut();
        pointcut.setExpression(
                properties.getBasePackages().stream()
                        .map(pkg -> "within(" + pkg + "..*)")
                        .collect(Collectors.joining(" || ")));

        MethodInterceptor advice =
                new RequestResponseLoggingAspect(
                        properties.getSlowCallThresholdMs(),
                        properties.getSuccessLogLevel(),
                        properties.getMaxRenderedLength());

        return new DefaultPointcutAdvisor(pointcut, advice);
    }
}
