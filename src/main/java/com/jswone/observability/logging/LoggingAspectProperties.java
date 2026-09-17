package com.jswone.observability.logging;

import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Getter
@Setter
@ConfigurationProperties(prefix = "jsw.observability.logging.aspect")
public class LoggingAspectProperties {

    /** Off by default: every consumer must explicitly opt in and name its own packages. */
    private boolean enabled = false;

    /**
     * Packages to intercept, e.g. {@code com.foo.controller,com.foo.service}. Combined into an
     * AspectJ {@code within(pkg..*) || within(pkg2..*)} pointcut expression. Empty (the default)
     * means the advisor matches nothing.
     */
    private List<String> basePackages = List.of();

    /** Calls at or above this duration are logged at WARN regardless of {@link #successLogLevel}. */
    private long slowCallThresholdMs = 2000;

    /**
     * SLF4J level (TRACE/DEBUG/INFO/WARN/ERROR) the success block is logged at. Defaults to DEBUG
     * so adopting this aspect doesn't add log volume until deliberately turned up; a service
     * migrating from always-on request/response logging can set this to INFO to keep prior
     * behavior.
     */
    private String successLogLevel = "DEBUG";

    /** Rendered argument/result strings longer than this are truncated. */
    private int maxRenderedLength = 2000;
}
