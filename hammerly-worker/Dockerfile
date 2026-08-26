FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /workspace
COPY pom.xml ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -DskipTests package

FROM eclipse-temurin:21-jre-noble AS runtime

ARG VCS_REF=unknown
LABEL org.opencontainers.image.source="https://github.com/jqiwen/Hammerly" \
      org.opencontainers.image.revision="${VCS_REF}"

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system --gid 10001 hammerly \
    && useradd --system --uid 10001 --gid hammerly --no-create-home --home-dir /app hammerly

WORKDIR /app
COPY --from=build --chown=10001:10001 /workspace/target/*.jar /app/app.jar

USER 10001:10001
ENV PORT=5002 \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"
EXPOSE 5002

HEALTHCHECK --interval=10s --timeout=3s --start-period=30s --retries=6 \
    CMD curl --fail --silent --show-error "http://localhost:${MANAGEMENT_PORT:-${PORT}}/actuator/health" || exit 1

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
