# Multi-stage build: UBI9 OpenJDK 21 (Maven) -> UBI9 OpenJDK 21 runtime
FROM registry.access.redhat.com/ubi9/openjdk-21:1.20 AS build
USER 185
WORKDIR /home/default/src
COPY --chown=185 pom.xml ./
COPY --chown=185 lib ./lib
COPY --chown=185 src ./src
RUN mvn -B -ntp package -DskipTests

FROM registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20
LABEL org.opencontainers.image.title="coolstore" \
      org.opencontainers.image.description="Coolstore monolith on Quarkus 3.15 / Java 21" \
      org.opencontainers.image.source="https://github.com/konveyor-ecosystem/coolstore"
ENV LANGUAGE='en_US:en' \
    JAVA_OPTS_APPEND="-Dquarkus.http.host=0.0.0.0 -Djava.util.logging.manager=org.jboss.logmanager.LogManager" \
    JAVA_APP_JAR="/deployments/quarkus-run.jar"
COPY --from=build --chown=185 /home/default/src/target/quarkus-app/lib/ /deployments/lib/
COPY --from=build --chown=185 /home/default/src/target/quarkus-app/*.jar /deployments/
COPY --from=build --chown=185 /home/default/src/target/quarkus-app/app/ /deployments/app/
COPY --from=build --chown=185 /home/default/src/target/quarkus-app/quarkus/ /deployments/quarkus/
EXPOSE 8080
USER 185
ENTRYPOINT [ "/opt/jboss/container/java/run/run-java.sh" ]
