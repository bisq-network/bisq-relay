# -----------------------------------------------------------------------------
# Build stage
# Compiles the application and produces the Spring Boot executable JAR.
# -----------------------------------------------------------------------------
FROM gradle:7.6.3-jdk17 AS build

WORKDIR /workspace

# Copy the project sources into the build container.
COPY . .

# Build the application, skipping tests since they should already have run
# as part of the CI pipeline.
RUN gradle clean bootJar -x test

# -----------------------------------------------------------------------------
# Runtime stage
# Creates a minimal image containing the JRE and application.
# -----------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy

# Install curl for the Docker health check, remove package metadata,
# and create a dedicated non-root user to run the application.
RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --system bisq-relay \
    && useradd --system \
        --gid bisq-relay \
        --home-dir /app \
        --no-create-home \
        --shell /usr/sbin/nologin \
        bisq-relay

# Application working directory.
WORKDIR /app

# Copy the Spring Boot executable and assign ownership to the application user.
COPY --from=build \
    --chown=bisq-relay:bisq-relay \
    /workspace/build/libs/bisq-relay-*.jar \
    /app/bisq-relay.jar

# Run the application as the non-root user.
USER bisq-relay

# Expose application traffic.
EXPOSE 8081

# Expose management/actuator for Prometheus.
EXPOSE 9400

ENTRYPOINT ["java", "-jar", "/app/bisq-relay.jar"]
