# ---- Build stage ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /project
COPY pom.xml .
COPY local-maven-repo ./local-maven-repo
RUN mvn -q -DskipTests dependency:go-offline || true
COPY src ./src
RUN mvn -q -DskipTests package

# ---- Runtime stage: UBI9 OpenJDK 21, fast-jar, non-root ----
FROM registry.access.redhat.com/ubi9/openjdk-21:1.20
ENV LANGUAGE='en_US:en' JAVA_OPTS_APPEND='-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager'
WORKDIR /deployments
COPY --from=build /project/target/quarkus-app/ ./
EXPOSE 8080
USER 185
ENTRYPOINT ["/opt/jboss/container/java/run/run-java.sh"]
