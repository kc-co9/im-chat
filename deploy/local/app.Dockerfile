ARG SKYWALKING_AGENT_IMAGE=apache/skywalking-java-agent:9.7.0-java21
FROM ${SKYWALKING_AGENT_IMAGE} AS skywalking-agent

FROM eclipse-temurin:21.0.12_8-jre-jammy
WORKDIR /app

ARG JAR_FILE
COPY --from=skywalking-agent /skywalking/agent /skywalking/agent
COPY ${JAR_FILE} /app/app.jar

ENTRYPOINT ["java", "-javaagent:/skywalking/agent/skywalking-agent.jar", "-jar", "/app/app.jar"]
