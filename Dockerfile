# Build the bootJar
FROM gradle:7.6.3-jdk17 AS build
WORKDIR /workspace
COPY . .
RUN gradle clean bootJar -x test

# Copy the required files
FROM eclipse-temurin:17-jre-jammy
WORKDIR /app
COPY --from=build /workspace/build/libs/bisq-relay-*.jar /app/bisq-relay.jar
COPY apnsCertificate.production.p12 .
COPY apnsCertificatePassword.txt .
COPY fcmServiceAccountKey.json .

# Expose application traffic port
EXPOSE 8080
# Expose management/actuator port for Prometheus
EXPOSE 9400

ENTRYPOINT ["java", "-jar", "/app/bisq-relay.jar"]
