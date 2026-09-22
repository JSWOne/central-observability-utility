package com.jswone.observability.model;

import java.time.Instant;
import java.util.Map;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorEventLog {

    private final String eventName;
    private final String system;
    private final String logId;
    @Builder.Default
    private final Instant time = Instant.now();
    private final String failureReason;
    private final String payload;
    @Builder.Default
    private final Map<String, Object> attributes = Map.of();
}
