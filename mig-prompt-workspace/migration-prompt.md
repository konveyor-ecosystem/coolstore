# Migration Prompt

## Current Application Summary

The application is **CoolStore**, a retail storefront monolith built as a Java EE 7 WAR (packaging `war`, Java 8 source level) and manually deployed to a two-node JBoss EAP 7.4 cluster (`standalone-full-ha.xml`). The backend is ~1,785 lines of Java across 26 files in five packages (`model`, `service`, `rest`, `utils`, `persistence`), plus WebLogic compatibility shim classes (`weblogic.application.*`, `weblogic.i18n.logging.NonCatalogLogger`) that exist in-source to satisfy legacy imports. The UI is an AngularJS 1.x + PatternFly single-page frontend served from the WAR (`index.jsp`, `app/`, `partials/`, vendored `bower_components/`).

Business logic is implemented with EJB 3.2 session beans (`@Stateless` CatalogService, OrderService, ProductService, ShippingService, ShoppingCartOrderProcessor; `@Stateful` ShoppingCartService), a `@MessageDriven` bean (OrderServiceMDB) consuming the `topic/orders` JMS topic, and CDI beans for web-tier endpoints. Persistence uses JPA 2.1 with Hibernate against PostgreSQL through a JBoss-managed JTA datasource (`java:jboss/datasources/CoolstoreDS`), with Flyway 4.1.2 handling schema migrations (`V1_1__CreateSchema.sql`, `V1_2__AddInitialData.sql`). Authentication uses Keycloak v20 (realm `eap`, public client `eap-app`) via a client-side `keycloak.json`. A vendored internal library, `com.enterprise:audit-logging-library:1.0.0` (system-scoped local JAR), performs file-based audit logging in OrderService (`FileSystemAuditLogger` writing to `./device-inventory-audit-logs`).

The codebase carries several legacy integration debts that the migration must address: `InventoryNotificationMDB` connects to a WebLogic JMS broker via `t3://localhost:7001` and `WLInitialContextFactory` (a dead/legacy code path on JBoss); `StartupListener` extends the WebLogic `ApplicationLifecycleListener` shim; and JNDI `InitialContext` lookups are used directly. An rgctl knowledge-graph analysis (35,856 nodes / 125,562 edges over 1,232 files) detected 317 circular dependencies, 8 potential secret findings, and 1 vulnerable taint flow, and produced a package-level migration order of `model → utils → service → rest → persistence`. A Konveyor-style ruleset in `rules/` (`audit-logging-migration`, labels `konveyor.io/source=openjdk8`, `konveyor.io/target=quarkus3`) defines five mandatory rules for upgrading the audit library to 2.0.0 on Java 21.

**Technology Stack:**
- Java EE 7 (EJB 3.2, JPA 2.1/Hibernate, JAX-RS 2.0, CDI 1.1, JMS 2.0, Servlet 3.0) on Java 8
- JBoss EAP 7.4 (standalone-full-ha, 2-node cluster), manual WAR deployment via jboss-cli
- PostgreSQL database (external container, port 5432)
- Keycloak v20.0.5 OIDC (realm `eap`, public client `eap-app`, client-side JS adapter)
- Apache ActiveMQ Artemis (embedded in EAP, `topic/orders` JMS topic)
- `com.enterprise:audit-logging-library:1.0.0` (local system-scoped JAR, file-based audit logging)
- Flyway 4.1.2 for schema migration, triggered at startup via `@Singleton DataBaseMigrationStartup`
- Maven 3 build (WAR packaging, `maven.test.skip=true`)
- Frontend: AngularJS 1.x, PatternFly, jQuery, vendored via bower; JSP entry point

**Architecture:**
- Monolithic 3-tier architecture: AngularJS SPA → JAX-RS REST layer → EJB services → JPA/Hibernate → PostgreSQL
- REST API: `RestApplication` with `/cart` (session-scoped CartEndpoint), `/orders`, `/products` (request-scoped endpoints)
- Asynchronous order flow: `ShoppingCartOrderProcessor` (EJB) publishes order JSON to `topic/orders`; `OrderServiceMDB` consumes and persists orders + updates inventory
- CDI producers for `EntityManager` (`Resources`) and `java.util.logging.Logger` (`Producers`)
- WebLogic compatibility shims in-source (`weblogic.application.*`, `weblogic.i18n.logging.NonCatalogLogger`)
- Session state: `@Stateful` ShoppingCartService + `@SessionScoped` CartEndpoint (HTTP session affinity implied by `<distributable/>`)

**Scale:**
- ~1,785 lines of Java (26 files), 5 functional packages, 8 JPA entities/models
- 3 REST resources (cart, orders, products), 9 service beans, 2 MDBs (1 active, 1 legacy WebLogic path)
- Frontend: ~2,300 LOC of AngularJS app code + vendored bower libraries
- rgctl graph: 35,856 nodes, 125,562 edges, 1,232 files indexed

## Target Platform and Technologies

**Target Stack:**
- Quarkus 3.x (latest LTS) on Java 21
- Quarkus extensions replacing Java EE APIs: quarkus-resteasy-reactive (or quarkus-rest) for JAX-RS, quarkus-hibernate-orm for JPA, quarkus-oidc for Keycloak integration, quarkus-smallrye-reactive-messaging (or quarkus-artemis-jms) for JMS/topic migration, quarkus-scheduler/lifecycle events for startup beans
- `com.enterprise:audit-logging-library:2.0.0` (upgrade from 1.0.0 per the mandatory `audit-logging-migration` ruleset — v2 requires Java 21+): replace `AuditEvent.builder()` with direct record instantiation, replace `FileSystemAuditLogger` with `StreamableAuditLogger` over TCP, replace synchronous `logEvent`/`logSuccess`/`logFailure` with non-blocking `logEventAsync(AuditEvent)`
- Migration rationale: repository rules (`rules/rule.yaml`) explicitly target `konveyor.io/target=quarkus3` from `konveyor.io/source=openjdk8`; the v2.0.0 audit library JAR is already vendored in `lib/`; Quarkus provides native cloud/Kubernetes readiness (fast startup, low memory, health probes, metrics out of the box)

**Deployment Platform:**
- Red Hat OpenShift (Kubernetes-based container platform), latest stable
- The existing `pom.xml` contains a `TODO: Add OpenShift profile here` placeholder — this migration delivers it

**Infrastructure Requirements:**
- Container runtime: Podman (already used by the team per README) or Docker
- Orchestration: Kubernetes on OpenShift
- CI/CD: OpenShift Pipelines (Tekton) or equivalent GitOps workflow
- Database: retain PostgreSQL (external managed service or OpenShift-persistent deployment), accessed via Quarkus `quarkus.datasource.*` configuration (replacing JBoss datasource/JNDI)
- Identity: retain Keycloak (realm `eap`, client `eap-app`), integrated server-side via quarkus-oidc instead of the client-side JS adapter
- Messaging: AMQ Broker (Artemis) on OpenShift or equivalent managed topic service for `topic/orders`

## Migration Approach

**Strategy:** Phased migration (incremental re-platform within one repository, strangler-style where practical)

**Reasoning:**
- The Java footprint is small (~1,785 LOC, 5 packages), but the migration is transformation-heavy: EJB/JMS/CDI semantics, a mandatory audit-library major-version upgrade (breaking API changes), Java 8 → 21 (11 major versions), and JBoss → Quarkus runtime swap. A phased approach isolates risk and keeps the application verifiable at each step.
- rgctl's `migration_plan.json` (hybrid preset, scheduled order) independently recommends package-by-package migration in the order `model → utils → service → rest → persistence`, which maps cleanly onto phases.
- The two-node cluster/session distribution requirement (`<distributable/>`, `@Stateful`/`@SessionScoped` cart) must be re-achieved on OpenShift with replicas — phased rollout allows parallel-run verification.

**Phases:**
1. Foundation: Quarkus 3 skeleton, Java 21 toolchain, build/CI, audit-library v2.0.0 dependency, PostgreSQL + Flyway wiring, Keycloak OIDC
2. Core migration by package (following rgctl order): `model` (JPA entities) → `utils` (producers, transformers, startup) → `service` (EJB→CDI services, JMS order flow, audit logging upgrade) → `rest` (JAX-RS endpoints, session/cart state) → `persistence` (datasource/EntityManager producer)
3. Legacy cleanup: remove WebLogic shims and the dead WebLogic JMS path (InventoryNotificationMDB)
4. Containerization and OpenShift deployment with health probes, metrics, scaling
5. Verification: characterization tests, E2E flows, performance baseline, cutover

**Key Considerations:**
- `javax.*` → `jakarta.*` namespace migration (Quarkus 3 is Jakarta EE 10-based)
- EJB `@Stateless`/`@Stateful`/`@Singleton`/`@MessageDriven` have no direct Quarkus equivalents — map to `@ApplicationScoped` CDI beans, and JMS MDBs to SmallRye/Artemis message consumers; `@Stateful` cart service and `@SessionScoped` endpoint need a state strategy (e.g., session replication or stateless cart with client-side cart ID)
- `@PersistenceContext`/`Resources.java` EntityManager producer → `quarkus-hibernate-orm` with `@Inject` EntityManager and `persistence.xml` simplification
- JNDI lookups (`InitialContext`, `java:/topic/orders`, `WLInitialContextFactory`) must be eliminated; JMS connection becomes configuration-driven
- Audit library v2 is a breaking upgrade (builder → record, file → TCP streaming, sync → async) — see the five mandatory rules in `rules/rule.yaml`
- Flyway runs from `@Singleton` startup bean → Quarkus Flyway extension (`quarkus.flyway.migrate-at-start`)
- Client-side Keycloak JS adapter → quarkus-oidc (server-side Bearer token validation)
- Frontend: retain AngularJS SPA as static resources (`META-INF/resources`) — no frontend rewrite in scope, but `coolstore.json` config (MONOLITH flag, API endpoints) must keep working
- 317 circular dependencies and 8 potential secrets flagged by rgctl should be triaged during migration; secrets must move to OpenShift Secrets/ConfigMaps

## Required Deliverables

### 1. Containerization
- Containerize the Quarkus application using a multi-stage `Containerfile`/`Dockerfile` (Maven builder stage → minimal runtime image, JVM mode; native compilation optional stretch goal)
- Container image must run as non-root, expose health/metrics endpoints, and be scannable (Trivy/Clair or OpenShift built-in)
- Registry strategy: OpenShift internal image registry

### 2. OpenShift Deployment
- Kubernetes/OpenShift manifests (or Helm chart) for the application Deployment with ≥2 replicas replacing the 2-node JBoss cluster
- Services, Routes, health/readiness probes (Quarkus `q/health`), and resource limits/requests
- ConfigMaps for application configuration; Secrets for database credentials, Keycloak client secrets, and audit-logging TCP endpoint settings (replacing the 8 potential in-code secrets flagged by rgctl)
- OpenShift Maven profile (`pom.xml` `openshift` profile) as pre-marked by the repository TODO
- Rolling update deployment strategy with rollback procedure
- Supporting services wiring: PostgreSQL, Keycloak, AMQ/Artemis broker for `topic/orders`

### 3. Test Coverage
- **Characterization tests**: capture current behavior before/alongside migration — cart add/update/remove/checkout flows, order JSON message contract on `topic/orders`, product/catalog queries, inventory update on order consumption
- **Unit tests**: ≥80% coverage on migrated service and REST code
- **Integration tests**: REST endpoints against real PostgreSQL (Testcontainers or Quarkus `@QuarkusTest`), JMS order flow end-to-end, Keycloak-secured endpoints, audit logging v2 event emission (TCP)
- **End-to-end tests**: complete user journey — browse catalog → add to cart → sign in (Keycloak) → checkout → order persisted → inventory decremented
- **Performance tests**: baseline current JBoss deployment (startup time, memory, throughput) and demonstrate parity or improvement on Quarkus/OpenShift

### 4. Detailed Migration Plan
- Phase-by-phase breakdown with dependencies, effort estimates, and owners
- Task lists mapped to the rgctl migration order (`model → utils → service → rest → persistence`) with blast-radius awareness for high-risk symbols (OrderService, ShoppingCartService, OrderServiceMDB)
- Risk assessment and mitigation strategies (including audit-library v2 breaking changes, session state, JMS semantics)
- Rollback procedures per phase
- Monitoring and observability setup (Quarkus metrics → Prometheus/OpenShift monitoring, structured logging)
- Documentation: migration decision log, updated README/runbook, configuration reference
- Training plan for the team on Quarkus 3 and OpenShift

## Success Criteria

- All components running on OpenShift: Quarkus 3 app (≥2 replicas), PostgreSQL, Keycloak, messaging for `topic/orders` — no JBoss EAP dependency remaining
- Application builds with Java 21 and the `audit-logging-library:2.0.0` (all five mandatory `audit-logging-*` rules satisfied — no `AuditEvent.builder()`, no `FileSystemAuditLogger`, no synchronous `logEvent`/`logSuccess`/`logFailure`)
- No WebLogic shim classes or WebLogic JMS/JNDI code paths remain
- Full storefront user journey works: browse → cart → Keycloak sign-in → checkout → order persisted → inventory updated → audit events streamed asynchronously
- Test coverage targets met (≥80% unit, all service boundaries integration-tested, E2E green)
- Performance parity or better vs. the JBoss baseline (startup, memory, latency)
- Health/readiness probes green; rolling updates and rollback demonstrated
- Team has runbook, documentation, and a training session on the new stack

## Constraints and Risks

**Constraints:**
- Target stack is fixed by repository rules: Quarkus 3 + Java 21 + audit-logging-library 2.0.0 (`rules/rule.yaml`, mandatory category)
- Deployment platform is OpenShift (per `pom.xml` TODO and migration standards)
- Database remains PostgreSQL; existing Flyway migrations must be preserved and extended, not rewritten (data continuity)
- Keycloak realm `eap` configuration (`realm-export.json`) is retained
- Frontend AngularJS SPA is preserved as-is (served as static resources); no UI framework migration in scope
- Team size/timeline not specified — assume a small team (2–4 engineers) with Java EE background learning Quarkus; plan includes ramp-up
- Builds currently skip tests (`maven.test.skip=true`) — this must be reversed in the migrated build

**Risks:**
- EJB semantics loss: `@Stateful` ShoppingCartService and `@SessionScoped` CartEndpoint rely on HTTP session state; incorrect replacement breaks multi-replica cart behavior — mitigate with characterization tests of cart flows and an explicit state strategy decision early
- JMS semantics change: MDB auto-acknowledge and topic pub/sub on a two-node cluster must be preserved by the Quarkus messaging consumer; message contract (`Transformers.shoppingCartToJson`) must stay stable
- Audit library v2 breaking API (builder→record, file→TCP, sync→async): requires a reachable TCP audit endpoint in all environments; effort per rule up to 3 points each
- Java 8 → 21 jump may surface removed APIs and stricter bytecode/date handling; incremental compile-fix cycles expected
- 317 circular dependencies detected by rgctl complicate package-by-package migration — resolve or isolate the worst offenders during the `service` phase
- 8 potential secrets and 1 taint flow flagged by rgctl — must be moved to OpenShift Secrets before go-live
- Keycloak client-side → server-side OIDC switch changes the login flow; the SPA's token handling must be verified against `realm-export.json`

---

**Request**: Create a comprehensive migration plan following the above requirements. The plan should include specification, design, task breakdown, and user stories suitable for immediate execution by the development team.
