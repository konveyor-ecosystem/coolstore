# CoolStore Monolith

The CoolStore monolith — originally a Java EE 7 WAR deployed on JBoss EAP 7.4 —
has been modernized to **Quarkus 3.15.1 / Java 21**. It is a standalone
fast-jar (`target/quarkus-app/quarkus-runner.jar`), containerized with a
Red Hat UBI 9 OpenJDK 21 base image (see [`Containerfile`](Containerfile)) and
deployed to OpenShift with the manifests in [`openshift/`](openshift/).
CI/CD is a GitHub Actions pipeline (`.github/workflows/build-openshift.yml`)
that builds, tests, pushes the image to Quay and rolls it out on OpenShift.

![coolstore](assets/coolstore.png "coolstore")

## Architecture

CoolStore is a single-deployment Quarkus 3 / Java 21 monolith: the AngularJS
front end (served from `src/main/webapp`) talks to JAX-RS REST endpoints under
`/services` (products, inventory, cart, orders). Checkout publishes order
events to the `topic/orders` JMS topic on an external ActiveMQ Artemis broker;
the catalog and orders live in PostgreSQL with the schema managed by Flyway;
authentication is delegated to Keycloak via OpenID Connect (realm `eap`,
public client `eap-app`, validated server-side by `quarkus-oidc`). All runtime
configuration is externalized as environment variables (see
[Configuration Reference](#configuration-reference)), so one image runs in any
environment.

## Build & Run

Prerequisites: JDK 21, Maven 3.9+.

```bash
# Development mode (live reload on http://localhost:8080)
mvn quarkus:dev

# Production build (fast-jar)
mvn package
java -jar target/quarkus-app/quarkus-runner.jar
```

Local development needs a PostgreSQL database, an ActiveMQ Artemis broker and
a Keycloak instance reachable at the endpoints below — start them with
[podman](#local-backing-services), or override the corresponding environment
variables / `application.properties` entries.

### Local backing services

PostgreSQL (matching the defaults in the
[Configuration Reference](#configuration-reference)):

```bash
podman run --name coolstore-postgres -p 5432:5432 \
  -e POSTGRES_USER=coolstore -e POSTGRES_PASSWORD=coolstore -e POSTGRES_DB=coolstore \
  -d postgres:16
```

ActiveMQ Artemis broker (with the `topic/orders` address used for checkout):

```bash
podman run --name coolstore-artemis -p 61616:61616 \
  -e AMQ_USER=coolstore -e AMQ_PASSWORD=coolstore \
  -e AMQ_PROTOCOLS=core -e AMQ_ADDRESSES=topic/orders \
  -d quay.io/artemiscloud/activemq-artemis-broker:latest
```

Keycloak — start in dev mode on port 8081, then import the `eap` realm and
create a test user (also valid for the legacy deployment):

1. `podman run --name coolstore-keycloak -p 8081:8080 -e KC_BOOTSTRAP_ADMIN_USERNAME=admin -e KC_BOOTSTRAP_ADMIN_PASSWORD=admin -d quay.io/keycloak/keycloak:26.0 start-dev`
2. Open <http://127.0.0.1:8081>, log in with the admin credentials above.
3. "Master" dropdown → *Create Realm* → *Browse* → select `realm-export.json`
   from this repo → *Create* → the `eap` realm is created.
4. *Users* → *Create new user* (e.g. `user1`) → *Credentials* → *Set password*
   (uncheck *Temporary*) → *Save*.
5. Sign in on the CoolStore UI with that user to run a checkout.

## Configuration Reference

All configuration is injected via environment variables (on OpenShift, from
the ConfigMap and Secrets in
[`openshift/coolstore-quarkus.yaml`](openshift/coolstore-quarkus.yaml)):

| Environment variable | Purpose | Default (local dev) |
|---|---|---|
| `QUARKUS_DATASOURCE_URL` | JDBC URL of the PostgreSQL database | `jdbc:postgresql://localhost:5432/coolstore` |
| `QUARKUS_DATASOURCE_USERNAME` | Database user | `coolstore` |
| `QUARKUS_DATASOURCE_PASSWORD` | Database password | `coolstore` |
| `QUARKUS_ARTEMIS_URL` | ActiveMQ Artemis broker URL (`tcp://…`) | `tcp://localhost:61616` |
| `QUARKUS_ARTEMIS_USERNAME` | Broker user | `coolstore` |
| `QUARKUS_ARTEMIS_PASSWORD` | Broker password | `coolstore` |
| `QUARKUS_OIDC_AUTH_SERVER_URL` | OIDC issuer (Keycloak realm `eap`) | `http://localhost:8081/realms/eap` |
| `AUDIT_LOGGER_TCP_HOST` | TCP host of the audit log collector | `localhost` |
| `AUDIT_LOGGER_TCP_PORT` | TCP port of the audit log collector | `1514` |

See `src/main/resources/application.properties` for the exact local-dev values
these defaults reflect. In-cluster values (e.g.
`jdbc:postgresql://postgresql:5432/coolstore`,
`http://keycloak:8080/realms/eap`) are set in the ConfigMap.

## Testing

```bash
mvn verify
```

Tests run under the Quarkus `%test` profile against an in-memory H2 database;
JMS broker consumers and OIDC authentication are disabled for tests, so no
PostgreSQL/Artemis/Keycloak instances are required to run the suite.

## Container & OpenShift

Build the container image locally:

```bash
podman build -f Containerfile -t quay.io/coolstore/coolstore-quarkus:latest .
```

Deploy to OpenShift (in dependency order — or one-shot, `oc` applies the
files in the listed order):

```bash
oc apply -f openshift/postgresql.yaml        # 1. database
oc apply -f openshift/amq-broker.yaml        # 2. JMS broker
oc create configmap keycloak-realm \         # 3a. realm prerequisite
  --from-file=realm-export.json=realm-export.json
oc apply -f openshift/keycloak.yaml          # 3b. identity provider
oc apply -f openshift/coolstore-quarkus.yaml # 4. the application
```

`oc apply -f openshift/` applies all files at once — Keycloak pods simply wait
for the `keycloak-realm` ConfigMap to exist. Replace every `CHANGE_ME` value
in the Secrets before deploying to a real cluster. The application is exposed
through the edge-TLS `coolstore` Route; it runs 2 replicas and autoscales
2–5 pods at 80% CPU.

## Documentation

- [`docs/migration-mapping.md`](docs/migration-mapping.md) — legacy → Quarkus
  concern mapping and migration-rule compliance
- [`docs/persistence-runbook.md`](docs/persistence-runbook.md) — Flyway schema
  management, backup/restore, rollback notes
- [`docs/operations-runbook.md`](docs/operations-runbook.md) — deploy, scale,
  probe, rollback and troubleshooting procedures
- [`docs/security-oidc.md`](docs/security-oidc.md) — OIDC flow, realm/client
  configuration and token troubleshooting

## Legacy deployment (JBoss EAP 7.4) — rollback reference

The pre-migration WAR deployment remains valid for rollback; condensed steps:

<details>
<summary>JBoss EAP 7.4 deployment (click to expand)</summary>

1. **PostgreSQL**: as above; add the JDBC driver as a JBoss module
   (`modules/org/postgresql/main` + `module.xml`).
2. **Start EAP**: `./standalone.sh -c standalone-full-ha.xml -Djboss.node.name=node1`.
3. **Configure** via `jboss-cli.sh --connect`:
   `/subsystem=datasources/jdbc-driver=postgresql:add(driver-name=postgresql,driver-module-name=org.postgresql)`;
   `data-source add --name=CoolstoreDS --jndi-name=java:jboss/datasources/CoolstoreDS --driver-name=postgresql --connection-url=jdbc:postgresql://127.0.0.1:5432/postgresDB --user-name=postgresUser --password=postgresPW`;
   `jms-topic add --topic-address=topic.orders --entries=topic/orders`.
4. **Deploy**: `mvn package` (legacy profile), `export JBOSS_HOME=…`, then
   `deploy ./target/ROOT.war` from `jboss-cli.sh`. UI at <http://127.0.0.1:8080>.
5. **Clustering**: start a second node with
   `standalone.sh -c standalone-full-ha.xml -Djboss.socket.binding.port-offset=100 -Djboss.node.name=node2`
   (empty `standalone/data/activemq` first); order events appear in both
   `standalone/log/server.log` files.

</details>

On OpenShift, clustering is replaced by Deployment replicas (2) plus the HPA —
see [`docs/operations-runbook.md`](docs/operations-runbook.md) for the
full rollback procedure.
