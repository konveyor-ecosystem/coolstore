# Migration Prompt

## Current Application Summary

The **CoolStore Monolith** is a Java EE 7 e-commerce application packaged as a WAR and historically deployed on JBoss EAP / WildFly (with legacy WebLogic stubs present in the codebase). It implements a full online retail store covering product catalogue browsing, shopping cart management, order processing, inventory management, promotions, and shipping calculations. The application is a classic three-tier monolith: a JAX-RS REST API layer, a CDI/EJB service layer, and JPA entities persisted via a JNDI-managed datasource (`java:jboss/datasources/CoolstoreDS`).

Analysis of the knowledge graph (rgctl discover with `--with-cfg`, `--with-taint`, `--with-harmonic`, `--export-migration-hints`) identified 15,244 total functions and 2,824 classes across 1,273 indexed files, with 18,513 detected communities. The Java application source itself is concentrated in five subsystem communities: `com.redhat.coolstore.model`, `com.redhat.coolstore.persistence`, `com.redhat.coolstore.rest`, `com.redhat.coolstore.service`, and `com.redhat.coolstore.utils`. The front-end is a single-page AngularJS application bundled inside the WAR with Bower-managed dependencies (Angular, Patternfly, D3, C3, Bootstrap).

The rgctl security scan found **3 potential secrets** embedded in the codebase. A taint analysis identified **1 vulnerable taint flow**. There are **232 circular dependencies** (primarily in the front-end Bower layer). A proprietary third-party JAR (`com.enterprise:audit-logging-library:1.0.0`) is bundled as a `system`-scoped Maven dependency, used exclusively in `OrderService` for file-system-based audit logging.

**Technology Stack:**
- **Language:** Java 8 (source/target 1.8)
- **Specification:** Java EE 7 (EJB 3.2, CDI 1.2, JAX-RS 2.0, JPA 2.1, JMS 2.0)
- **Application Server:** JBoss EAP / WildFly (JNDI datasource, JMS Topic `java:/topic/orders`)
- **Legacy stubs:** WebLogic `ApplicationLifecycleListener` and `WLInitialContextFactory` (vendor lock-in)
- **Persistence:** JPA 2.1 with Hibernate, Flyway 4.1.2 for schema migration
- **Build:** Apache Maven 3, WAR packaging (`maven-war-plugin 3.2.0`)
- **Messaging:** JMS 2.0 — `@MessageDriven` MDBs consuming `topic/orders`; `InventoryNotificationMDB` uses WebLogic T3 JNDI (`t3://localhost:7001`)
- **Remote EJB:** `ShippingService` exposed as `@Remote` EJB, looked up via WildFly JNDI (`org.wildfly.naming.client.WildFlyInitialContextFactory`)
- **Front-end:** AngularJS 1.x SPA with Patternfly 3, D3, C3, Bower dependency management
- **Security/Auth:** Keycloak (configured via `keycloak.json` in WAR)
- **Proprietary dependency:** `com.enterprise:audit-logging-library:1.0.0` (file-system audit logger, system-scoped JAR at `lib/`)

**Architecture:**
- **Pattern:** Monolithic 3-tier — REST endpoints → EJB/CDI services → JPA entities
- **Key components and responsibilities:**
  - `CartEndpoint` — Session-scoped JAX-RS resource managing cart lifecycle (add, delete, checkout, merge)
  - `ProductEndpoint` — Request-scoped JAX-RS resource for product catalogue reads
  - `OrderEndpoint` — Request-scoped JAX-RS resource for order history reads
  - `ShoppingCartService` — `@Stateful` EJB; holds per-session cart state, delegates to `PromoService`, `ProductService`, `ShoppingCartOrderProcessor`
  - `ShoppingCartOrderProcessor` — `@Stateless` EJB; publishes cart JSON to JMS `topic/orders`
  - `OrderServiceMDB` — `@MessageDriven` bean; consumes orders from topic, persists via `OrderService`, updates inventory via `CatalogService`
  - `InventoryNotificationMDB` — WebLogic-coupled MDB; connects to WebLogic T3 to subscribe to `topic/orders` for low-stock alerts (vendor lock-in)
  - `CatalogService` — `@Stateless` EJB; JPA CRUD for `CatalogItemEntity` / `InventoryEntity`
  - `OrderService` — `@Stateless` EJB; JPA CRUD for `Order`; initialises `FileSystemAuditLogger` on startup
  - `ShippingService` — `@Stateless @Remote` EJB; pricing logic for shipping and insurance
  - `PromoService` — `@ApplicationScoped` CDI bean; in-memory promotion rule engine
  - `ProductService` — `@Stateless` EJB; delegates to `CatalogService`, transforms entities to `Product` DTOs via `Transformers`
  - `DataBaseMigrationStartup` — `@Singleton @Startup` EJB; runs Flyway migration on boot
  - `StartupListener` — WebLogic `ApplicationLifecycleListener` (vendor lock-in hook)
  - `Transformers` — Utility class; JSON ↔ Order/Cart marshalling using `javax.json`
  - `Resources` / `Producers` — CDI producers for `EntityManager` and `Logger`
- **Integration points:**
  - JMS Topic (`java:/topic/orders`) — internal async order/inventory notification
  - WebLogic T3 remote JMS (`t3://localhost:7001`) — `InventoryNotificationMDB` vendor coupling
  - Remote EJB (`ejb:/ROOT/ShippingService`) — WildFly JNDI lookup for shipping pricing
  - Keycloak — SSO/OIDC authentication
  - JNDI datasource — `java:jboss/datasources/CoolstoreDS`
  - Flyway — schema lifecycle management

**Scale (from rgctl graph):**
- 31 Java source files across 5 packages
- ~1,500 lines of Java application code (excluding stubs and front-end)
- 5 JAX-RS endpoints / 12 REST operations
- 8 EJBs (2 Stateless sets, 1 Stateful, 1 Singleton, 2 MDBs, 1 Remote)
- 3 CDI-only beans
- 8 JPA entities / model classes
- 1 proprietary bundled library (audit logger)
- 1 AngularJS SPA front-end

---

## Target Platform and Technologies

**Target Stack:**
- **Runtime:** Quarkus 3.x (latest LTS stream) on Java 17
- **REST layer:** Quarkus RESTEasy Reactive (JAX-RS 3.1 / Jakarta EE 10 annotations)
- **Persistence:** Hibernate ORM with Panache (Quarkus extension), JPA 3.1
- **Messaging:** SmallRye Reactive Messaging with AMQ Broker / ActiveMQ Artemis (replacing JMS MDBs)
- **Transactions:** Narayana (Quarkus built-in; replaces EJB container-managed transactions)
- **Security:** Quarkus OIDC extension with Keycloak (drop-in replacement)
- **Audit logging:** Replace proprietary `FileSystemAuditLogger` with structured logging (Quarkus JBoss Logging + optional OpenTelemetry audit events)
- **Schema migration:** Flyway extension for Quarkus
- **Build:** Maven with Quarkus Maven plugin; native image capability via GraalVM (optional)
- **Front-end:** Retain AngularJS SPA or migrate to Patternfly 5 React (separate workstream — out of scope for this plan)
- **Migration rationale:** Quarkus provides a natural migration path from Java EE / WildFly, sharing CDI, JAX-RS, JPA, and Flyway APIs. It delivers fast startup, low memory footprint, and cloud-native readiness suitable for OpenShift container deployment.

**Deployment Platform:**
- Red Hat OpenShift 4.x (Kubernetes-based container platform)
- OpenShift Pipelines (Tekton) for CI/CD
- OpenShift Service Mesh (Istio) for inter-service traffic management (optional phase 2)

**Infrastructure Requirements:**
- Container runtime: Podman / Buildah (OpenShift builds)
- Orchestration: Kubernetes on OpenShift
- CI/CD: OpenShift Pipelines (Tekton) with GitOps (ArgoCD)
- Message broker: AMQ Broker (ActiveMQ Artemis) deployed on OpenShift
- Database: PostgreSQL on OpenShift (replacing WildFly-managed datasource)
- Secret management: OpenShift Secrets + Vault integration (for 3 detected secrets)
- Image registry: OpenShift internal registry or Quay.io

---

## Migration Approach

**Strategy:** Phased migration — Strangler Fig pattern

**Reasoning:**
- Application is a small but tightly-coupled monolith with vendor-specific constructs (WebLogic stubs, WildFly JNDI, remote EJB). These must be systematically replaced, not big-banged.
- The five Java packages naturally decompose into migration layers: model → persistence → service → REST → messaging/utils.
- The rgctl migration plan orders these packages by harmonic centrality (connectivity-weighted): `model` first, `persistence` next, then `utils`, `service`, and finally `rest` (highest harmonic — most connected, highest risk last).
- Zero-downtime requirement during the transition period.
- The proprietary audit library requires a replacement strategy before `OrderService` can be migrated.

**Phases:**
1. **Foundation** — Jakarta EE 10 namespace migration, project restructure, Quarkus scaffold, CI/CD pipeline
2. **Model & Persistence** — Migrate entities and JPA layer to Quarkus Panache
3. **Services & Business Logic** — Migrate EJB services to CDI beans (Quarkus), replace PromoService
4. **Messaging & Remote EJB** — Replace JMS MDBs with Reactive Messaging, eliminate WebLogic/WildFly coupling
5. **REST Layer & Security** — Migrate JAX-RS endpoints, wire Keycloak OIDC
6. **Audit & Utility Replacement** — Replace proprietary audit logger, migrate Flyway, replace WebLogic lifecycle hooks
7. **Containerisation & Deployment** — Containerise, deploy to OpenShift, configure AMQ/PostgreSQL
8. **Validation & Cutover** — Integration tests, performance baseline, traffic cutover

**Key Considerations:**
- `javax.*` → `jakarta.*` namespace change (EE 8 → EE 10) throughout all Java sources
- `@Stateful` EJB (`ShoppingCartService`) → CDI `@SessionScoped` bean (Quarkus supports HTTP session scope)
- EJB container-managed transactions → `@Transactional` (CDI/Narayana)
- `@MessageDriven` MDBs → `@Incoming` SmallRye Reactive Messaging consumers on AMQ Artemis topic
- WebLogic T3 JNDI lookup in `InventoryNotificationMDB` → reactive subscriber on shared broker topic
- Remote EJB `ShippingService` → in-process CDI bean (no longer needs JNDI remote lookup)
- `@Singleton @Startup` `DataBaseMigrationStartup` → Quarkus Flyway extension (auto-runs at boot)
- Proprietary `FileSystemAuditLogger` → custom `AuditService` using structured JBoss Logging or OpenTelemetry events
- WebLogic `ApplicationLifecycleListener` stubs → Quarkus `@ApplicationScoped` CDI observers on `StartupEvent`/`ShutdownEvent`
- Keycloak integration → `quarkus-oidc` extension (configuration migrated from `keycloak.json` to `application.properties`)
- 3 embedded secrets → OpenShift Secrets + environment variable injection
- Flyway 4.1.2 → Flyway 9.x (Quarkus extension); SQL migration scripts carry forward unchanged

---

## Required Deliverables

### 1. Containerisation
- Multi-stage `Containerfile` for Quarkus JVM mode (fast) and optional native mode (GraalVM)
- Layered image with Quarkus fast-jar layout
- Image scanned with OpenShift ACS / Trivy
- Internal OpenShift registry or Quay.io as target registry

### 2. OpenShift Deployment
- Kubernetes manifests: `Deployment`, `Service`, `Route`, `ConfigMap`, `Secret`
- AMQ Broker `ActiveMQArtemis` custom resource (AMQ Broker Operator)
- PostgreSQL `Deployment` or CrunchiData PGO operator instance
- Keycloak realm export → OpenShift-deployed Keycloak (RHSSO or Keycloak Operator)
- Rolling deployment strategy with readiness (`/q/health/ready`) and liveness (`/q/health/live`) probes
- Resource limits: requests 256Mi/0.25 CPU, limits 512Mi/1 CPU (baseline; tune after load test)
- HorizontalPodAutoscaler based on CPU/memory metrics
- OpenShift Pipelines (Tekton) pipeline: build → test → scan → push → deploy

### 3. Test Coverage
- **Characterisation tests:** Capture current REST API contracts (CartEndpoint, ProductEndpoint, OrderEndpoint) using REST-assured against the running monolith before migration begins
- **Unit tests:** ≥80% line coverage on all service and utility classes; JUnit 5 + Mockito
- **Integration tests:** Quarkus `@QuarkusIntegrationTest` with `@QuarkusTestResource` for PostgreSQL (Testcontainers) and AMQ Artemis (Testcontainers)
- **End-to-end tests:** REST-assured scenarios covering: add-to-cart → checkout → order saved → inventory decremented → shipping calculated
- **Performance baseline:** Gatling or k6 load test — establish req/sec, p95 latency, error rate on monolith; validate target on Quarkus after migration
- **Security tests:** OWASP ZAP scan on REST endpoints; validate taint flow remediation

### 4. Detailed Migration Plan
- Phase-by-phase task breakdown with owners, story points, and dependencies (see `mig-plan-workspace/tasks.md`)
- User stories linking business value to technical tasks (see `mig-plan-workspace/UserStory.md`)
- Risk register with mitigation strategies
- Rollback procedure: maintain WAR deployment alongside Quarkus pod during parallel-run window
- Monitoring: OpenShift monitoring stack (Prometheus + Grafana) with Quarkus Micrometer metrics
- Documentation: Updated README, ADRs for key architectural decisions, operator runbook

---

## Success Criteria

- All Java EE components running on Quarkus 3.x, deployed as containers on OpenShift
- Zero WebLogic/WildFly vendor-specific code remaining in the codebase
- All 12 REST operations return identical responses to the monolith (characterisation tests green)
- ≥80% unit test coverage on migrated service and utility code
- Integration tests pass against AMQ Artemis and PostgreSQL on OpenShift
- Startup time < 5 seconds (JVM mode); < 1 second (native, optional)
- p95 request latency ≤ monolith baseline under equivalent load
- Proprietary audit library fully replaced with open-source equivalent
- 0 critical/high CVEs in container image
- OpenShift CI/CD pipeline runs end-to-end on every PR
- Team trained on Quarkus, Reactive Messaging, and OpenShift operations

---

## Constraints and Risks

**Constraints:**
- Proprietary `audit-logging-library-1.0.0.jar` has no source available; replacement must be functionally equivalent (file-system audit log in `./device-inventory-audit-logs/`)
- Keycloak realm configuration (`realm-export.json`) must be preserved exactly — user/role mappings cannot change during migration
- Front-end AngularJS SPA is out of scope for this migration phase

**Risks:**

| Risk | Likelihood | Impact | Mitigation |
|------|-----------|--------|-----------|
| `@Stateful` EJB session semantics differ from CDI `@SessionScoped` | Medium | High | Implement session affinity (`JSESSIONID`) or move to stateless cart model backed by DB/Redis |
| Proprietary audit JAR replacement functional gaps | Medium | Medium | Reverse-engineer JAR API; write adapter that writes same log format |
| WebLogic T3 `InventoryNotificationMDB` behaviour undocumented | High | Medium | Rewrite as pure reactive subscriber; test with AMQ Artemis from day one |
| Flyway version gap (4.1.2 → 9.x) baseline/repair behaviour changes | Low | Medium | Test migration scripts in staging before cutover; use `flyway repair` if checksums differ |
| 3 embedded secrets exposed in codebase | High | High | Rotate all secrets immediately; inject via OpenShift Secrets before first deployment |
| 232 circular dependencies in front-end Bower layer | Low | Low | Front-end out of scope; note for future workstream |
| 1 taint flow identified by rgctl | Medium | High | Review and remediate before production deployment |

---

**Request:** Create a comprehensive migration plan following the above requirements. The plan should include specification, design, task breakdown, and user stories suitable for immediate execution by the development team.
