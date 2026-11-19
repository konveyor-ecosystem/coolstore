# CoolStore monolith (Quarkus 3 / Java 21) — multi-stage build, Red Hat UBI only.
#
# Stage 1 builds the fast-jar with Maven on UBI9 OpenJDK 21 (image ships JDK 21 + Maven).
# Stage 2 runs it on the JRE-only UBI9 OpenJDK 21 runtime image (smaller attack surface),
# as non-root UID 185 via the image's run-java.sh launcher.

# ---- Build stage: UBI9 OpenJDK 21 (JDK + Maven) ----
FROM registry.access.redhat.com/ubi9/openjdk-21:1.20 AS build
USER root
WORKDIR /project
COPY pom.xml .
COPY local-maven-repo ./local-maven-repo
RUN mvn -q -DskipTests dependency:go-offline || true
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Runtime stage: UBI9 OpenJDK 21 runtime (JRE only), fast-jar, non-root ----
FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20
ENV LANGUAGE='en_US:en' \
    JAVA_OPTS_APPEND='-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager'
WORKDIR /deployments
COPY --from=build --chown=185:0 /project/target/quarkus-app/ ./
EXPOSE 8080
USER 185
# Container-level health check (OpenShift uses the Deployment probes; this covers standalone podman/docker)
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD curl -f http://localhost:8080/q/health/live || exit 1
ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]
