# Containerfile for the CoolStore monolith on Quarkus 3 (Java 21, fast-jar).
#
# Multi-stage build:
#   1. "builder" — Maven + Eclipse Temurin 21, produces target/quarkus-app/.
#   2. runtime   — Red Hat UBI 9 OpenJDK 21, runs as non-root uid 185.
#
# Build:  podman build -f Containerfile -t quay.io/coolstore/coolstore-quarkus:latest .
# Run:    podman run -p 8080:8080 quay.io/coolstore/coolstore-quarkus:latest
#
# NOTE: all runtime configuration is injected as environment variables from
# OpenShift Secrets / ConfigMaps (see openshift/coolstore-quarkus.yaml):
#
#   QUARKUS_DATASOURCE_URL / QUARKUS_DATASOURCE_USERNAME / QUARKUS_DATASOURCE_PASSWORD
#   QUARKUS_ARTEMIS_URL / QUARKUS_ARTEMIS_USERNAME / QUARKUS_ARTEMIS_PASSWORD
#   QUARKUS_OIDC_AUTH_SERVER_URL
#   AUDIT_LOGGER_TCP_HOST / AUDIT_LOGGER_TCP_PORT
#
# No credentials or endpoints are baked into the image.

# ---------------------------------------------------------------------------
# Stage 1: build the application
# ---------------------------------------------------------------------------
FROM docker.io/maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /usr/src/maven
# Cache dependency downloads first so source edits don't invalidate the layer.
COPY pom.xml .
# Publish the internal audit-logging-library 2.0.0 jar (com.enterprise) to the
# local Maven repository inside the builder — it is not on Maven Central.
COPY lib/audit-logging-library-2.0.0.jar /tmp/audit-logging-library-2.0.0.jar
RUN mvn -B -ntp install:install-file \
      -Dfile=/tmp/audit-logging-library-2.0.0.jar \
      -DgroupId=com.enterprise -DartifactId=audit-logging-library \
      -Dversion=2.0.0 -Dpackaging=jar
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp package -DskipTests

# ---------------------------------------------------------------------------
# Stage 2: runtime image
# ---------------------------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-21:latest
# uid 185 = standard unprivileged runtime user on Red Hat base images.
USER 185
WORKDIR /deployments
COPY --from=builder /usr/src/maven/target/quarkus-app/ /deployments/
EXPOSE 8080
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS_APPEND -jar /deployments/quarkus-runner.jar"]
