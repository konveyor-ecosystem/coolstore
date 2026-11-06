# Dockerfile for CoolStore on Quarkus 3 (Java 21, fast-jar) — Red Hat UBI only.
#
# Multi-stage build:
#   1. "builder" — ubi9/openjdk-21 (JDK 21 + Maven 3.9), builds target/quarkus-app/
#   2. runtime   — ubi9/openjdk-21-runtime (JRE only, no build tools), uid 185
#
# Build:  podman build -f Dockerfile -t quay.io/coolstore/coolstore-quarkus:latest .
#         (docker build works identically)
# Run:    podman run -p 8080:8080 quay.io/coolstore/coolstore-quarkus:latest
#
# All runtime configuration is injected as environment variables from the
# OpenShift ConfigMap/Secrets (configmap.yaml, secret.yaml):
#   QUARKUS_DATASOURCE_URL / QUARKUS_DATASOURCE_USERNAME / QUARKUS_DATASOURCE_PASSWORD
#   QUARKUS_ARTEMIS_URL / QUARKUS_ARTEMIS_USERNAME / QUARKUS_ARTEMIS_PASSWORD
#   QUARKUS_OIDC_AUTH_SERVER_URL
#   AUDIT_LOGGER_TCP_HOST / AUDIT_LOGGER_TCP_PORT
# No credentials or endpoints are baked into the image.

# ---------------------------------------------------------------------------
# Stage 1: build the application (Red Hat UBI 9 OpenJDK 21, includes Maven)
# ---------------------------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-21:latest AS builder
# Build stage may run as root; it is discarded and never shipped.
USER root
WORKDIR /usr/src/maven
# Cache dependency downloads first so source edits don't invalidate the layer.
COPY pom.xml .
# Publish the internal audit-logging-library 2.0.0 jar (com.enterprise) to the
# local Maven repository inside the builder — it is not on Maven Central.
COPY lib/audit-logging-library-2.0.0.jar /tmp/audit-logging-library-2.0.0.jar
RUN mvn -B -ntp install:install-file \
      -Dfile=/tmp/audit-logging-library-2.0.0.jar \
      -DgroupId=com.enterprise -DartifactId=audit-logging-library \
      -Dversion=2.0.0 -Dpackaging=jar \
  && mvn -B -ntp dependency:go-offline
COPY src ./src
# Tests run in CI (`mvn verify`); image builds skip compiling/running them.
RUN mvn -B -ntp package -Dmaven.test.skip=true

# ---------------------------------------------------------------------------
# Stage 2: runtime image (UBI 9 OpenJDK 21 *runtime* — JRE only)
# ---------------------------------------------------------------------------
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:latest
# uid 185 = default unprivileged runtime user on Red Hat OpenJDK base images.
USER 185
WORKDIR /deployments
COPY --from=builder --chown=185:0 /usr/src/maven/target/quarkus-app/ /deployments/quarkus-app/
EXPOSE 8080
ENV JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
# Best-effort TCP liveness for plain `podman/docker run` (no extra packages).
# OpenShift ignores HEALTHCHECK and uses the HTTP probes on /q/health/* that
# deployment.yaml defines — this is only a local-run convenience.
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS_APPEND -jar /deployments/quarkus-app/quarkus-runner.jar"]
