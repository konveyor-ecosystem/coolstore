# Migration Prompt

## Current Application Summary

The application is the **Red Hat CoolStore Monolith**, a Java EE 7 e-commerce storefront packaged as a single WAR (`ROOT.war`) and deployed to JBoss EAP 7.4 in clustered `standalone-full-ha` mode. It provides product catalog browsing, shopping cart management, pricing (promotions, shipping fees, insurance), checkout, order persistence, and inventory management. Analysis (rgctl knowledge graph: 1,232 files, 35,856 nodes, 125,562 edges) shows a classic layered monolith with a Java EE service tier and an AngularJS 1.x frontend served from the same WAR.

The application has significant legacy platform coupling. Although it currently runs on JBoss EAP, it carries **Oracle WebLogic heritage**: in-repo `weblogic.*` compatibility shim classes (`weblogic.application.ApplicationLifecycleListener`, `weblogic.application.ApplicationLifecycleEvent`, `weblogic.i18n.logging.NonCatalogLogger`), a `StartupListener` that extends the WebLogic lifecycle listener, and an `InventoryNotificationMDB` that connects as a raw WebLogic JMS client (`WLInitialContextFactory`, `t3://localhost:7001`). The service tier also performs an unnecessary **remote EJB JNDI lookup** (`ejb:/ROOT/ShippingService!...` via `WildFlyInitialContextFactory`) to call a `ShippingService` that lives in the same application.

A custom enterprise **audit-logging-library 1.0.0** (system-scoped Maven jar in `lib/`) is used by `OrderService` via `FileSystemAuditLogger` writing to `./device-inventory-audit-logs`. The repository ships a Konveyor ruleset (`rules/ruleset.yaml`) with 5 **mandatory** rules requiring upgrade to audit-logging-library **2.0.0** (Java 21+, `StreamableAuditLogger` over TCP, `AuditEvent` records, non-blocking `logEventAsync`), labeled `konveyor.io/source=openjdk8`, `konveyor.io/target=quarkus3`.

**Technology Stack:**
- Java EE 7 (javax.* namespace): JAX-RS 2.0, CDI 1.1, EJB 3.2 (@Stateful, @Stateless, @Singleton @Startup, @MessageDriven), JPA 2.1 (Hibernate on EAP), JMS 2.0 (ActiveMQ Artemis on EAP), JSON-P 1.0
- Java 8 (maven-compiler source/target 1.8), Maven WAR packaging, finalName `ROOT`
- PostgreSQL via JBoss datasource `java:jboss/datasources/CoolstoreDS`; Flyway 4.1.2 executed at startup (`DataBaseMigrationStartup` @Singleton @Startup); schema managed by `V1_1__CreateSchema.sql`, `V1_2__AddInitialData.sql` (tables: PRODUCT_CATALOG, INVENTORY, ORDERS, ORDER_ITEMS)
- Keycloak v20.0.5 OIDC (realm `eap`, public client `eap-app`, adapter `keycloak.json` + `realm-export.json`; frontend uses keycloak.js adapter)
- AngularJS 1.x + PatternFly frontend (JSP `index.jsp`, vendored `bower_components`, 463 lines of app JS in `src/main/webapp/app/`)
- Custom `com.enterprise:audit-logging-library:1.0.0` (system-scoped, `lib/audit-logging-library-1.0.0.jar`); target version 2.0.0 also present in `lib/`
- WebLogic API shims compiled into the app; `web.xml` marked `<distributable/>` for two-node cluster testing
- CI: legacy GitHub Actions workflow deploying WAR to Azure Web App (to be replaced)

**Architecture:**
- Layered monolith: `rest` (3 JAX-RS resource classes under `/services`: cart, orders, products) → `service` (8 EJB/CDI beans: ShoppingCartService, ShoppingCartOrderProcessor, OrderService, OrderServiceMDB, InventoryNotificationMDB, CatalogService, ProductService, PromoService, ShippingService) → `model`/`persistence` (4 JPA entities: CatalogItemEntity, InventoryEntity, Order, OrderItem)
- Async order flow: checkout → `ShoppingCartOrderProcessor` publishes order JSON to JMS topic `java:/topic/orders` → `OrderServiceMDB` persists order and deducts inventory; `InventoryNotificationMDB` flags low inventory (< 50)
- State management: `CartEndpoint` is `@SessionScoped` and `ShoppingCartService` is `@Stateful` (per-user cart state); cart API is keyed by `cartId`
- Cross-cutting: CDI producers for `java.util.logging.Logger` and `EntityManager`; `@PostConstruct`/`@PreDestroy` audit-logger lifecycle in `OrderService`
- Frontend: AngularJS SPA calls `/services/cart` and `/services/products`; OIDC login via keycloak.js

**Scale:**
- ~1,800 lines of Java across 30 files in 7 packages (`com.redhat.coolstore.{model,service,rest,utils,persistence}` + 2 `weblogic.*` shim packages)
- rgctl app communities: model (97 members), service (39), rest (12), utils (9), persistence (1), weblogic shims (6); frontend communities for webapp app JS
- Community dependency edges (rgctl): rest→service (w=14), rest→model (w=17), service→model (w=37), service→utils (w=5), utils→model (w=23) — defines a clean bottom-up migration order
- 1,232 files indexed total (majority is vendored bower_components static assets); 1 vulnerable taint flow only in vendored gulp tooling (not application code)

## Target Platform and Technologies

**Target Stack:**
- Quarkus 3.x on Java 21 (LTS)
- Migration rationale: repository's own Konveyor ruleset (`rules/`) declares `konveyor.io/target=quarkus3` with source `openjdk8`; audit-logging-library 2.0.0 requires Java 21+; Quarkus provides container-first, fast-startup runtime with first-class OpenShift integration and native extensions for every current integration point (JAX-RS, JPA, JMS, OIDC, Flyway)
- Component mapping:
  - JAX-RS 2.0 (javax) → Quarkus REST (Jakarta REST, `quarkus-rest`/`quarkus-rest-jackson`)
  - CDI + EJB beans → CDI beans (`@ApplicationScoped`, `@SessionScoped` replacement strategy — see Migration Approach)
  - JPA 2.1/Hibernate → `quarkus-hibernate-orm` (same entities, Jakarta namespace)
  - JMS topic MDBs → `quarkus-artemis-jms` (JMS compatibility) or SmallRye Reactive Messaging
  - Flyway 4.1.2 → `quarkus-flyway` (managed at startup, same SQL migrations)
  - Keycloak OIDC → `quarkus-oidc` for backend token validation; keep keycloak.js frontend adapter
  - java.util.logging producers → JBoss Logging / SLF4J (`quarkus-logging-json` optional)
  - audit-logging-library 1.0.0 → 2.0.0 (`StreamableAuditLogger` over TCP, `AuditEvent` records, `logEventAsync`)

**Deployment Platform:**
- Red Hat OpenShift (Kubernetes-based container platform), latest stable
- Confirmed by repository intent: `pom.xml` contains `<!-- TODO: Add OpenShift profile here -->`

**Infrastructure Requirements:**
- Container runtime: Podman (or Docker) for local builds
- Orchestration: Kubernetes on OpenShift
- CI/CD: OpenShift Pipelines (Tekton) or GitHub Actions deploying to OpenShift (replacing the legacy Azure Web App workflow)
- Database: PostgreSQL (retain existing schema and data — zero data migration)
- Messaging: AMQ Broker (Artemis) on OpenShift with `topic/orders` topic (replaces EAP embedded ActiveMQ)
- Identity: Keycloak (Red Hat build of Keycloak) on OpenShift, realm `eap` imported from `realm-export.json`

## Migration Approach

**Strategy:** Phased migration (layer-by-layer rewrite into a new Quarkus 3 application, with the legacy WAR remaining deployable until cutover)

**Reasoning:**
- The codebase is small (~1.8K LOC Java), so a full strangler-fig decomposition into microservices is unnecessary; a single phased modernization of the monolith to Quarkus minimizes risk and preserves behavior
- rgctl `migration_plan.json` (Hybrid Default preset, scheduled order) provides a dependency-ordered package sequence: **model → utils → service → rest → frontend assets → WebLogic shim removal → persistence configuration**
- Community dependency edges confirm bottom-up order is safe: model has no outgoing app dependencies; rest depends on service and model; service depends on model and utils
- Each phase produces a verifiable, testable increment; the legacy app remains the system of record until final cutover

**Phases:**
1. Foundation & Infrastructure — Quarkus 3 project skeleton, Java 21 toolchain, dependency baseline (audit-logging-library 2.0.0, Quarkus BOM), Containerfile, OpenShift manifests, CI pipeline
2. Persistence layer — Jakarta JPA entities, `quarkus-hibernate-orm`, `quarkus-flyway` with existing SQL migrations, PostgreSQL `CoolstoreDS` via environment config
3. Service layer — EJB → CDI conversion (drop remote EJB JNDI lookup in favor of local injection of `ShippingService`), JMS producer/consumers on `topic/orders` (AMQ Broker), audit-logging-library 2.0.0 integration in `OrderService`
4. REST layer — Jakarta REST endpoints under `/services`, cart state redesign (cartId-keyed stateless cart or Quarkus session handling replacing @SessionScoped/@Stateful)
5. Frontend & static assets — serve AngularJS app from Quarkus static resources (`META-INF/resources`), JSP `index.jsp` → static `index.html`, keycloak.js adapter unchanged
6. Platform & cutover — Keycloak OIDC validation on `/services/*`, health/readiness probes (replacing `health.jsp`), WebLogic shim deletion, OpenShift deployment, parallel run and rollback rehearsal

**Key Considerations:**
- `javax.*` → `jakarta.*` namespace migration across all Java sources
- Session state: `@SessionScoped CartEndpoint` + `@Stateful ShoppingCartService` hold per-user cart state in memory; the API is already cartId-keyed, so cart state should become stateless (server-side store or signed cart payload) — must preserve multi-tab behavior noted in code comments
- Remote EJB elimination: `ShoppingCartService.lookupShippingServiceRemote()` calls same-app `ShippingService` via JNDI — replace with direct CDI injection of `ShippingServiceRemote`
- JMS semantics: `OrderServiceMDB` (auto-acknowledge) and `InventoryNotificationMDB` (WebLogic client, manual subscription) → Artemis consumers; preserve topic fan-out behavior for both order persistence and inventory notification
- WebLogic removal: delete `weblogic.*` shim packages and `StartupListener`; replace lifecycle hooks with Quarkus `@Observes StartupEvent`
- Mandatory audit library rules (all 5 must be satisfied): dependency 1.x→2.0.0 with Java 21; `AuditEvent.builder()` → record instantiation; `FileSystemAuditLogger` → `StreamableAuditLogger` over TCP; `logEvent` → `logEventAsync`; `logSuccess`/`logFailure` → full `AuditEvent` + `logEventAsync`
- Data preservation: reuse PostgreSQL schema and Flyway migrations unchanged; no data migration required
- Frontend compatibility: AngularJS app calls `/services/cart`, `/services/products` — keep API paths and JSON shapes identical
- Clustering: `<distributable/>`/HA pair testing must be replaced by OpenShift replicas + Artemis topic subscriptions

## Required Deliverables

### 1. Containerization
- All components containerized with Podman/Docker using multi-stage builds (Maven builder → Quarkus JVM image; native compilation optional stretch goal)
- Image best practices: non-root user, minimal base (ubi9/openjdk-21), layer caching
- Security scanning of container images; registry strategy (OpenShift internal registry)
- AMQ Broker and Keycloak containerized (operators or images) with the PostgreSQL database

### 2. OpenShift Deployment
- Kubernetes/OpenShift manifests for all components: Deployment, Service, Route, ConfigMap, Secret, StatefulSet where applicable
- Deployment strategy: rolling updates for the Quarkus app
- Health checks: liveness/readiness probes via Quarkus `quarkus-smallrye-health` (replacing `health.jsp` and WebLogic lifecycle logging)
- Resource limits, requests, and horizontal pod autoscaling configuration
- Externalized configuration: datasource URL/credentials, Keycloak URL, Artemis URL, audit-logger TCP endpoint via environment/ConfigMaps/Secrets (no secrets in source)
- Keycloak realm `eap` imported from `realm-export.json`; OIDC bearer-token validation on `/services/*` endpoints

### 3. Test Coverage
- **Characterization tests** (capture current behavior BEFORE migration):
  - Pricing rules: promotion 25% off item 329299; free shipping at cart total ≥ 75; shipping tiers (2.99/4.99/6.99/8.99/10.99 by cart total bands); insurance percentages (2%/1.5%/1%); cart item dedupe logic
  - API contracts: `/services/cart/{cartId}` GET/POST/DELETE shapes; `/services/products` and `/services/orders` JSON responses
  - Order flow: checkout → JSON order on topic `topic/orders` → order persisted, inventory deducted, low-inventory threshold (50) alert
- **Unit tests**: minimum 80% coverage for migrated code
- **Integration tests**: PostgreSQL, Artemis, Keycloak via Testcontainers; audit logger TCP stream verification
- **End-to-end tests**: complete business workflow (browse catalog → login via Keycloak → add to cart → checkout → order persisted → inventory updated)
- **Performance tests**: baseline current EAP deployment; target: equal or better latency/throughput on Quarkus JVM

### 4. Detailed Migration Plan
- Phase-by-phase breakdown with tasks, owners, and dependencies (dependency-ordered per rgctl migration plan: model → utils → service → rest → frontend → shim removal → persistence/config)
- Risk assessment and mitigation strategies (session state, JMS semantics, OIDC, audit library API changes)
- Rollback procedures (legacy WAR remains deployable; blue/green cutover on OpenShift)
- Monitoring and observability: Quarkus metrics (Micrometer/Prometheus), structured logging, OpenShift console dashboards
- Documentation: runbook, architecture notes, configuration reference
- Training plan for the team on Quarkus 3, Java 21, and OpenShift

## Success Criteria

- All components (app, PostgreSQL, AMQ Broker, Keycloak) running on Red Hat OpenShift
- Zero data loss: same PostgreSQL schema and data; Flyway migrations unchanged and idempotent
- All 5 mandatory `rules/audit-logging-migration` rules satisfied (library 2.0.0, Java 21, `StreamableAuditLogger`, `logEventAsync`, `AuditEvent` records)
- All `weblogic.*` dependencies and shim classes removed from the codebase
- Remote EJB JNDI lookup eliminated (local CDI injection)
- Test coverage: ≥80% unit coverage; all characterization, integration, and E2E tests passing (including checkout workflow)
- API compatibility: frontend AngularJS app works unchanged against new backend paths/JSON
- Performance at or above baseline; startup time improved vs. EAP
- Team trained and documentation complete

## Constraints and Risks

**Constraints:**
- Java 21 is a hard requirement (audit-logging-library 2.0.0 and target ruleset)
- The 5 rules in `rules/rule.yaml` are category **mandatory** — the migration is not complete until all pass
- Target platform standardized on Red Hat OpenShift (per pom.xml intent and standardization policy)
- Preserve API paths and JSON contracts for the AngularJS frontend
- Preserve database schema (no data migration)

**Risks:**
- javax→jakarta namespace and Java 8→21 upgrade may surface latent compilation issues (e.g., JAXB `@XmlRootElement` on `InventoryEntity` — JAXB removed from JDK)
- Loss of @SessionScoped/@Stateful semantics could break multi-tab cart behavior — mitigate with cartId-keyed stateless cart design plus characterization tests
- JMS consumer semantics (auto-acknowledge, topic fan-out to two consumer types) may behave differently under Artemis/Quarkus — mitigate with integration tests on real broker
- `InventoryNotificationMDB` is a raw WebLogic JMS client that may currently be inert; confirm expected behavior before migration
- Keycloak token validation differences (public client + keycloak.js vs. quarkus-oidc bearer validation) may reject frontend tokens — mitigate with E2E test against real Keycloak
- audit-logging-library 2.0.0 API differences (TCP stream endpoint configuration) — mitigate with integration test and configuration externalization
- Legacy Azure CI workflow must be replaced, not reused
- Mitigation strategies: phased cutover with parallel run, rollback to legacy WAR, characterization tests written first

---

**Request**: Create a comprehensive migration plan following the above requirements. The plan should include specification, design, task breakdown, and user stories suitable for immediate execution by the development team.
