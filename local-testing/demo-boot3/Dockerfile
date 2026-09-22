# Mirrors the JSW production convention (javaagent at /opt/opentelemetry-javaagent.jar), but
# pulls the agent from the public release instead of the private otel-java21 base image, so this
# harness runs without GCP credentials.
FROM curlimages/curl:8.11.1 AS agent
ARG OTEL_AGENT_VERSION=2.11.0
RUN curl -sSLo /tmp/opentelemetry-javaagent.jar \
    https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v${OTEL_AGENT_VERSION}/opentelemetry-javaagent.jar

FROM eclipse-temurin:21-jre
COPY --from=agent /tmp/opentelemetry-javaagent.jar /opt/opentelemetry-javaagent.jar
COPY target/demo-boot3-0.0.1-SNAPSHOT.jar /app/app.jar
ENTRYPOINT ["java", "-javaagent:/opt/opentelemetry-javaagent.jar", "-jar", "/app/app.jar"]
