# Migration Mapping: Java EE 7 → Quarkus 3

Mapping of every legacy concern to its Quarkus 3.15.1 / Java 21 replacement.
Legacy column describes the application as deployed on JBoss EAP 7.4; the
Quarkus column describes the modernized `coolstore-quarkus` application
(`com.redhat.coolstore:coolstore-quarkus`).

| Concern | Legacy (Java EE 7 / EAP 7.4) | Quarkus 3.15.1 |
|---|---|---|
| Packaging | `ROOT.war` deployed into EAP | Fast-jar `target/quarkus-app/quarkus-runner.jar`, `java -jar`; containerized via `Dockerfile` (UBI 9 OpenJDK 21) |
| Dependency management | `javaee-api` 7.0 (`provided`) | `io.quarkus.platform:quarkus-bom` 3.15.1 import |
| Session beans | `@Stateful` / `@Stateless` / `@Singleton @Startup` EJBs | CDI beans with `@ApplicationScoped` / `@SessionScoped` / `@RequestScoped`; boot work in `StartupEvent` observers |
| Order processing | `@MessageDriven` on `topic/orders` | `quarkus-artemis-jms` topic subscriber (`MessageListener`) on `topic/orders` |
| JMS access | Container-injected `JMSContext` | Injected `ConnectionFactory`; `JMSContext` created/closed per send |
| JPA configuration | `META-INF/persistence.xml` (`CoolstoreDS`) | `quarkus.hibernate-orm.*` properties + `QUARKUS_DATASOURCE_*` env vars |
| Schema migration | `DataBaseMigrationStartup` `@Singleton` calling Flyway 4.1.2 `baseline()` + `migrate()` | `quarkus-flyway` with `migrate-at-start` (see persistence-runbook) |
| EntityManager | `Resources.java` `@Produces` producer | Container-managed `@Inject EntityManager` |
| Logging | `java.util.logging` `Logger` producers | `org.jboss.logging.Logger` (JBoss Logging) |
| JSON building | `javax.json` (`Json.createObjectBuilder`) | Jackson (`ObjectMapper`, POJOs/records) |
| XML/JSON binding | `@XmlRootElement` (JAXB) | Jackson serialization (annotated POJOs/records) |
| Cart endpoint | `@SessionScoped` endpoint holding cart state | `@RequestScoped` REST resource + stateless cart service keyed by `cartId` (cart state in PostgreSQL) |
| EJB remoting | Remote EJB JNDI lookups | Local CDI injection (`@Inject`) |
| WebLogic shims/lifecycle | WebLogic compat shims, lifecycle listeners | CDI `StartupEvent` / `ShutdownEvent` observers |
| Health endpoint | `health.jsp` | `quarkus-smallrye-health`: `/q/health`, `/q/health/live`, `/q/health/ready`, `/q/health/started` |
| CI/CD | Azure Web App pipeline (`main_clustertesting(dev).yml`) | GitHub Actions `.github/workflows/build-openshift.yml` (build → Quay image → OpenShift deploy) |
| Clustering | `<distributable/>` EAP full-HA domain, 2 nodes | OpenShift `Deployment` (2 replicas) + `HorizontalPodAutoscaler` (2–5, 80% CPU) |
| Audit logging | `FileSystemAuditLogger` writing local files | `StreamableAuditLogger` streaming over TCP (`AUDIT_LOGGER_TCP_HOST/PORT`, default `audit-collector:1514`) |
| Audit events | `AuditEvent.builder()` builder pattern | `AuditEvent` Java 21 record, direct instantiation |
| Audit emission | Synchronous `logEvent(event)` / `logSuccess` / `logFailure` | Non-blocking `logEventAsync(event)` with full `AuditEvent` record |

## Rules compliance

The five **mandatory** migration rules (`rules/rule.yaml`, `audit-logging-0001`
… `audit-logging-0005`) and how the Quarkus application satisfies them:

| Rule | Requirement | How it is satisfied |
|---|---|---|
| `audit-logging-0001` | Upgrade `com.enterprise.audit-logging-library` from 1.x to 2.0.0 and use Java 21 | `pom.xml` depends on `audit-logging-library` **2.0.0** (`lib/audit-logging-library-2.0.0.jar`) and builds with Java 21 on Quarkus 3.15.1 |
| `audit-logging-0002` | Replace deprecated `AuditEvent.builder()` with direct record instantiation | Checkout audit code constructs the `AuditEvent` record directly (`new AuditEvent(...)`) |
| `audit-logging-0003` | Replace `FileSystemAuditLogger` instantiation with `StreamableAuditLogger` over TCP | The audit logger is a `StreamableAuditLogger` configured from `AUDIT_LOGGER_TCP_HOST` / `AUDIT_LOGGER_TCP_PORT`; no file-system logger remains |
| `audit-logging-0004` | Use non-blocking `logEventAsync(event)` instead of synchronous `logEvent(event)` | All audit emissions go through `logEventAsync(...)`; the request path never blocks on the collector |
| `audit-logging-0005` | Replace `logSuccess` / `logFailure` with a full `AuditEvent` record | Removed convenience methods; every audit emission builds a complete `AuditEvent` record and passes it to `logEventAsync` |

Operational consequence of rule 0003/0004: a TCP audit collector must be
reachable at `AUDIT_LOGGER_TCP_HOST:1514` (in-cluster `audit-collector:1514`);
configure it before enabling production traffic, and expect audit events to be
emitted asynchronously (failures are logged, they never fail the request).

## Verification

Quick checks that the mapping above is fully in place:

```bash
mvn -B -ntp verify                                  # tests pass (%test: H2, no broker, no OIDC)
java -jar target/quarkus-app/quarkus-runner.jar     # fast-jar boots (was: WAR on EAP)
curl -fsS localhost:8080/q/health/ready             # SmallRye health replaces health.jsp
curl -fsS localhost:8080/q/metrics                  # Micrometer metrics
oc -n coolstore get deploy,hpa                      # replicas + HPA replace HA clustering
oc -n coolstore logs deployment/coolstore-quarkus   # JSON structured logs
```

CI (`.github/workflows/build-openshift.yml`) runs the same build on every push
and pull request; on `main` it also builds and pushes the container image and
applies the root manifests to the cluster.
