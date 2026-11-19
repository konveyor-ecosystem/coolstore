# Coolstore monolith — Red Hat UBI multi-stage container build
#
# Stage 1 (builder): UBI8 OpenJDK + pinned Apache Maven -> builds the Java EE 7 WAR
# Stage 2 (runtime): UBI8 OpenJDK + WildFly 26.1 (javax/Jakarta EE 8 compatible
#   upstream of JBoss EAP 7.x) running standalone-full.xml (full platform incl.
#   JMS/Artemis), PostgreSQL datasource + topic/orders configured via env vars.
#
# Build:  podman build -t coolstore-monolith:1.0.0 .
# Run:    podman run -p 8080:8080 -e DB_HOST=... -e DB_USER=... -e DB_PASSWORD=... coolstore-monolith:1.0.0

########################
# Stage 1: Maven build #
########################
FROM registry.access.redhat.com/ubi8/openjdk-11:latest AS builder

ARG MAVEN_VERSION=3.9.9
ENV MAVEN_HOME=/opt/maven

# Install pinned Maven from Apache (no network package repo dependency)
USER root
RUN curl -fsSL -o /tmp/maven.tar.gz \
      https://archive.apache.org/dist/maven/maven-3/${MAVEN_VERSION}/binaries/apache-maven-${MAVEN_VERSION}-bin.tar.gz \
    && echo "${MAVEN_SHA}  /tmp/maven.tar.gz" | sha256sum -c - \
    && tar -xzf /tmp/maven.tar.gz -C /opt \
    && ln -s /opt/apache-maven-${MAVEN_VERSION} ${MAVEN_HOME} \
    && rm -f /tmp/maven.tar.gz

WORKDIR /build
# Copy build descriptor + local system-scoped dependency first (layer caching)
COPY pom.xml ./
COPY lib/ ./lib/
# Copy sources and resources
COPY src/ ./src/

RUN mvn -B clean package -DskipTests \
    && mkdir -p /build/exploded \
    && cd /build/exploded \
    && jar xf /build/target/ROOT.war

##############################################
# Stage 2: WildFly runtime on UBI8 OpenJDK  #
##############################################
FROM registry.access.redhat.com/ubi8/openjdk-11:latest

ARG WILDFLY_VERSION=26.1.3.Final
ARG POSTGRES_JDBC_VERSION=42.7.4
ENV JBOSS_HOME=/opt/jboss/wildfly \
    LAUNCH_JBOSS_IN_BACKGROUND=0 \
    JAVA_OPTS="-Djava.net.preferIPv4Stack=true -Djava.awt.headless=true"

USER root
WORKDIR /opt

# WildFly 26.1 = last javax (Jakarta EE 8) line, upstream of EAP 7.4
RUN curl -fsSL -o /tmp/wildfly.tar.gz \
      https://download.jboss.org/wildfly/${WILDFLY_VERSION}/wildfly-${WILDFLY_VERSION}.tar.gz \
    && tar -xzf /tmp/wildfly.tar.gz -C /opt \
    && mv /opt/wildfly-${WILDFLY_VERSION} ${JBOSS_HOME} \
    && rm -f /tmp/wildfly.tar.gz

# PostgreSQL JDBC driver as a WildFly module
RUN curl -fsSL -o /tmp/postgresql.jar \
      https://repo1.maven.org/maven2/org/postgresql/postgresql/${POSTGRES_JDBC_VERSION}/postgresql-${POSTGRES_JDBC_VERSION}.jar \
    && ${JBOSS_HOME}/bin/jboss-cli.sh --command="module add \
         --name=org.postgresql \
         --resources=/tmp/postgresql.jar \
         --dependencies=javax.api,javax.transaction.api" \
    && rm -f /tmp/postgresql.jar

# Configure standalone-full.xml (offline, embedded server):
#  - PostgreSQL driver + CoolstoreDS datasource (env-driven connection)
#  - JMS topic 'orders' bound to topic/orders (consumed by OrderServiceMDB)
COPY scripts/wildfly-config.cli /tmp/wildfly-config.cli
RUN ${JBOSS_HOME}/bin/jboss-cli.sh --file=/tmp/wildfly-config.cli \
    && rm -f /tmp/wildfly-config.cli

# Exploded ROOT.war kept read-only in the image; the entrypoint copies it into
# the writable standalone/deployments dir (emptyDir volume in OpenShift) so
# keycloak.json can be patched at startup from KEYCLOAK_AUTH_SERVER_URL.
COPY --from=builder /build/exploded /opt/app/ROOT.war
COPY scripts/entrypoint.sh /opt/app/entrypoint.sh

RUN chown -R 1001:0 ${JBOSS_HOME} /opt/app \
    && chmod -R g+rwX ${JBOSS_HOME} /opt/app \
    && chmod +x /opt/app/entrypoint.sh

# Security: run as non-root (UID 1001, matches UBI OpenJDK default user)
USER 1001

EXPOSE 8080 8443

# Container orchestration health check (app serves /health.jsp at webapp root)
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
  CMD curl -fsS http://127.0.0.1:8080/health.jsp || exit 1

ENTRYPOINT ["/opt/app/entrypoint.sh"]
