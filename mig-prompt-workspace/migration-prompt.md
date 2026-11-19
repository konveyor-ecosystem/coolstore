# Migration Prompt

## Current Application Summary

The application is **CoolStore Monolith** (`com.redhat.coolstore:monolith:1.0.0-SNAPSHOT`), a Java EE 7 e-commerce storefront packaged as a single WAR (`ROOT.war`) and deployed to a JBoss EAP 7.4 cluster (`standalone-full-ha.xml`, two-node, `<distributable/>` web.xml). It compiles with Java 8 (`maven.compiler source/target 1.8`) and implements a product catalog, shopping cart, pricing/promotions, and order checkout workflow. Business logic is implemented with Enterprise JavaBeans — `@Stateful ShoppingCartService`, `@Stateless` services (`CatalogService`, `ProductService`, `OrderService`, `ShippingService`, `ShoppingCartOrderProcessor`), a `@Singleton @Startup DataBaseMigrationStartup` (Flyway), and a `@MessageDriven OrderServiceMDB` consuming the `topic/orders` JMS topic. Persistence uses JPA 2.1 (Hibernate) against PostgreSQL via the `java:jboss/datasources/CoolstoreDS` JTA data source, with four entities (`CatalogItemEntity`/`PRODUCT_CATALOG`, `InventoryEntity`/`INVENTORY`, `Order`/`ORDERS`, `OrderItem`/`ORDER_ITEMS`). REST APIs are exposed with JAX-RS under `@ApplicationPath("/services")` (`/cart`, `/orders`, `/products` — 9 route operations total). The web tier is a JSP-hosted AngularJS 1.5 front end with vendored `bower_components` (no build pipeline), secured by a Keycloak public client (`realm=eap`, `eap-app`) using the Keycloak JS adapter.

rgctl code-graph analysis (28,326 nodes / 86,565 edges across 1,273 indexed files) identifies the application's subsystems as distinct communities: `com.redhat.coolstore.model` (19 members), `com.redhat.coolstore.rest`, `com.redhat.coolstore.service`, `com.redhat.coolstore.persistence`, `com.redhat.coolstore.utils`, plus WebLogic-specific code — in-repo `weblogic.application` and `weblogic.i18n.logging` shim classes (`StartupListener` extends `weblogic.application.ApplicationLifecycleListener`) and an `InventoryNotificationMDB` that uses the WebLogic JMS client (`WLInitialContextFactory`, `t3://localhost:7001`, `TCF`). The graph also captures a self-referential remote-EJB lookup: `ShoppingCartService` performs a JNDI lookup (`WildFlyInitialContextFactory`, `ejb:/ROOT/ShippingService!`) to call `ShippingService`, which lives in the same WAR. A custom `audit-logging-library` 1.0.0 (system-scoped JAR in `lib/`) is used by `OrderService` (`FileSystemAuditLogger` writing to `./device-inventory-audit-logs`, `AuditEvent.Builder`-era API); version 2.0.0 is present in `lib/` and the repo's Konveyor ruleset (`rules/rule.yaml`, 5 mandatory rules, `konveyor.io/source=openjdk8`, `konveyor.io/target=quarkus3`) requires the 1.x → 2.x upgrade (Java 21+, `AuditEvent` record instantiation, `StreamableAuditLogger` over TCP, non-blocking `logEventAsync`).

**Technology Stack:**
- Java 8, Java EE 7 (`javax.*`: EJB 3.2, CDI 1.1, JPA 2.1, JAX-RS 2.0, JMS 2.0, JSON-P 1.0)
- JBoss EAP 7.4 (clustered, full-HA profile), WebLogic compatibility shims + WebLogic JMS client code
- PostgreSQL (JTA datasource `CoolstoreDS`), Hibernate via JPA, Flyway 4.1.2 (`db/migration/V1_1__CreateSchema.sql`, `V1_2__AddInitialData.sql`)
- Keycloak 20.0.5 (public client, `realm-export.json` in repo)
- `audit-logging-library` 1.0.0 (system-scoped JAR) — must upgrade to 2.0.0
- Maven WAR build; JSP + AngularJS 1.5 + vendored bower_components front end

**Architecture:**
- Monolithic 3-tier WAR: JAX-RS endpoints → CDI/EJB services → JPA entities → PostgreSQL
- Asynchronous order flow: cart checkout → `ShoppingCartOrderProcessor` publishes JSON to JMS topic `java:/topic/orders` → `OrderServiceMDB` (saves order, updates inventory) and `InventoryNotificationMDB` (WebLogic JMS subscriber, low-inventory alerts)
- `@Stateful` cart held in memory per session; `@SessionScoped` `CartEndpoint`; remote-EJB self-lookup for shipping calculation
- Startup: `DataBaseMigrationStartup` (Flyway) and `StartupListener` (WebLogic lifecycle)

**Scale:**
- ~1,712 lines of Java across 31 files (28 application classes + 3 WebLogic shims); 5 model POJOs + 4 JPA entities; 9 REST operations; 2 MDBs; 6 service classes
- rgctl graph: 28,326 nodes, 86,565 edges, 18,513 communities (modularity 0.74); bulk of indexed volume is vendored front-end JS (`bower_components`) — the migration scope for code is the Java backend plus webapp entry pages
- rgctl migration plan: 153 steps (Hybrid Default preset, scheduled order); Java package order: `weblogic.application` (9) → `coolstore.model` (17) → `weblogic.i18n.logging` (48) → `coolstore.persistence` (55) → `coolstore.rest` (70) → `coolstore.service` (93) → `coolstore.utils` (95); webapp JS: `app.services` (64), `app` (73), `app.controllers` (101), `app.directives` (49), `app.routes` (51), `partials` (53)

## Target Platform and Technologies

**Target Stack:**
- Quarkus 3.x (latest LTS) on Java 21 — matches the repo ruleset target `konveyor.io/target=quarkus3` and the audit-logging-library 2.x requirement (Java 21+)
- `jakarta.*` namespace (Jakarta EE 10): `quarkus-rest` (RESTEasy Reactive) replacing JAX-RS, `quarkus-hibernate-orm` replacing JPA/Hibernate on EAP, `quarkus-oidc` replacing the Keycloak JS/server adapter, `quarkus-flyway` replacing the hand-rolled `DataBaseMigrationStartup`
- `quarkus-messaging` (SmallRye Reactive Messaging, AMQP 1.0 via `quarkus-messaging-amqp` or Artemis) replacing JMS `@MessageDriven`/`JMSContext` topic flow
- EJB → CDI beans: `@Stateless` → `@ApplicationScoped`, `@Singleton @Startup` → `@ApplicationScoped` + `@Observes StartupEvent`, `@Stateful` cart → externalized cart state (or per-pod in-memory with documented limitations)
- Migration rationale: cloud-native, fast startup, low memory, first-class OpenShift/Kubernetes support, and direct alignment with the repo's Konveyor analysis rules

**Deployment Platform:**
- Red Hat OpenShift 4.x (Kubernetes-based container platform) — the pom.xml already contains a `<!-- TODO: Add OpenShift profile here -->` placeholder to fill in

**Infrastructure Requirements:**
- Container runtime: Podman (multi-stage builds)
- Orchestration: Kubernetes on OpenShift
- CI/CD: OpenShift Pipelines (Tekton) or GitOps (ArgoCD)
- Database: retain PostgreSQL (containerized, OpenShift `Database`/CrunchyDB or external managed service)
- Identity: retain Keycloak (containerized on OpenShift or Red Hat SSO) with OIDC
- Messaging: AMQP broker (ActiveMQ Artemis or Red Hat AMQ) replacing EAP embedded ActiveMQ

## Migration Approach

**Strategy:** Phased, incremental (strangler-lite) within a single replacement service, with a short parallel run before cutover.

**Reasoning:** The codebase is small (~1.7K LOC Java), making a full rewrite feasible, but the Java EE → Quarkus change touches every layer (namespace, DI model, persistence, messaging, security), so phased delivery with working software at each phase reduces risk. The rgctl migration plan's scheduled package order (shims → model → persistence → REST → service → utils) provides a dependency-aware sequence.

**Phases:**
1. Foundation & Build (Quarkus 3 skeleton, Java 21, Maven restructuring, audit-logging 2.x, CI)
2. Persistence & Domain (entities to `jakarta.persistence`, Flyway, `Resources`/`Producers` → CDI+Quarkus idioms)
3. Services & Messaging (EJBs → CDI, remote-EJB self-lookup removed, JMS → reactive messaging, WebLogic shims/JMS client removed)
4. REST API & Web Tier (`/services` endpoints, JSP → static assets, Keycloak → OIDC)
5. Containerization & OpenShift Deployment (image, manifests, secrets, probes, autoscaling)
6. Verification, Parallel Run & Cutover (characterization tests, load test, decommission EAP)

**Key Considerations:**
- `javax.*` → `jakarta.*` across all 31 Java files; `javax.json` (Transformers) → JSON-B/JSON-P
- Remove in-repo `weblogic.*` shim classes and `StartupListener`; delete WebLogic JMS client path in `InventoryNotificationMDB` (or re-implement as reactive messaging consumer)
- Eliminate the self-referential remote EJB JNDI lookup (`ejb:/ROOT/ShippingService!`) — inject `ShippingService` directly via CDI
- `@Stateful`/`@SessionScoped` cart semantics: document/externalize (HTTP session, or cart-id keyed state) for horizontal scaling on OpenShift
- `audit-logging-library` 1.x → 2.x per mandatory rules: `FileSystemAuditLogger` → `StreamableAuditLogger` (TCP), `AuditEvent.Builder` → record, `logEvent` → `logEventAsync`, remove `logSuccess`/`logFailure`
- Flyway 4.1.2 → current Flyway via `quarkus-flyway` (schema unchanged — reuse `V1_1`/`V1_2` scripts, add baseline strategy)
- JMS topic `topic/orders` ordering/acknowledgement semantics must be preserved in the messaging replacement
- Keycloak JS adapter is deprecated — move the front end to OIDC (or Bearer-token flows via `quarkus-oidc`)
- Database schema and data are retained (PostgreSQL) — no data migration, only connection/config changes
- Clustering (`<distributable/>`, two-node HA) → OpenShift replicas with readiness/liveness probes

## Required Deliverables

### 1. Containerization
- Containerize the Quarkus application with a multi-stage `Containerfile`/`Dockerfile` (Maven build stage → `registry.access.redhat.com/ubi9/openjdk-21` runtime stage)
- Build a JVM-mode image first; fast-jar packaging recommended
- Security scanning of container images (OpenShift integrated registry / Clair or Trivy in CI)
- Container registry strategy: OpenShift internal image stream

### 2. OpenShift Deployment
- Kubernetes/OpenShift manifests (or Helm chart) for the application Deployment, Service, Route
- Deployment strategy: rolling updates; canary for cutover if feasible
- Resource limits/requests and horizontal pod autoscaling (2+ replicas to preserve current HA posture)
- Health checks: liveness (`/q/health/live`) and readiness (`/q/health/ready`) probes replacing `health.jsp`
- ConfigMaps for quarkus application config; Secrets for DB credentials, Keycloak client secret, audit-log TCP endpoint
- Supporting services: PostgreSQL, Keycloak, AMQP broker (templates or operators)
- Fill in the pom.xml `<!-- TODO: Add OpenShift profile here -->` profile (e.g., `quarkus.container-image` / `oc` deploy)

### 3. Test Coverage
- **Characterization Tests**: capture current behavior before migration — cart pricing rules (shipping tiers, promotions incl. `329299` 25% promo, free shipping ≥ 75), checkout → order JSON shape (published by `Transformers.shoppingCartToJson`), inventory deduction, REST contract for all 9 operations under `/services`
- **Unit Tests**: minimum 80% coverage for new/changed code (pricing, promotions, shipping, transformers, services)
- **Integration Tests**: REST endpoints with database (Testcontainers PostgreSQL), messaging flow (order publish → consume → persist → inventory update)
- **End-to-End Tests**: browse → add to cart → checkout → order persisted, with OIDC login
- **Performance Tests**: baseline current EAP deployment; target parity (startup, memory footprint expected to improve)

### 4. Detailed Migration Plan
- Phase-by-phase breakdown with timelines and task-level breakdown (see `mig-plan-workspace/tasks.md`)
- Task lists with owners, estimates, and dependencies
- Risk assessment and mitigation strategies (below)
- Rollback procedures (keep EAP deployment available for 30 days post-cutover; DB schema unchanged so rollback is redeployment)
- Monitoring and observability: Quarkus metrics (`/q/metrics`), structured logs replacing `System.out.println` in MDBs and audit log streaming
- Documentation: migration runbook, updated README for the new stack
- Training plan for the team on Quarkus and OpenShift

## Success Criteria

- All components (application, database, Keycloak, broker) running on OpenShift with 2+ application replicas
- All 9 REST operations behave identically under `/services` (contract verified by characterization tests)
- Checkout flow works end-to-end with Keycloak OIDC login; orders persist; inventory decrements
- audit-logging-library 2.0.0 in use with `StreamableAuditLogger` + `logEventAsync` (all 5 mandatory Konveyor rules pass: `rgctl rules` / analysis clean)
- No `weblogic.*` or self-referential EJB lookup code remains; no `javax.*` imports remain
- ≥80% unit test coverage; integration and E2E suites green in CI
- Container image scanned; no critical CVEs; image builds in CI pipeline
- EAP 7.4 deployment decommissioned after a 2-week stable parallel run

## Constraints and Risks

**Constraints:**
- Target is fixed by analysis rules: Quarkus 3 / Java 21 (`konveyor.io/target=quarkus3`)
- PostgreSQL schema must be retained (no data migration budget)
- The front end (AngularJS 1.5 + bower_components) is out of scope for a UI framework rewrite — serve it as static assets; only the JSP shell and auth adapter must change
- audit-logging-library is a local system-scoped JAR — must be installed to a repository or vendored properly for container builds (2.0.0 required for Java 21)

**Risks:**
- `@Stateful`/`@SessionScoped` cart state is in-memory: scaling to multiple pods without externalizing state may break tabbed multi-cart behavior — mitigate by keeping cart state keyed per cart-id in a shared store or accepting per-pod affinity initially
- JMS → reactive messaging changes delivery semantics (topic fan-out, auto-acknowledge) — mitigate with integration tests covering both `OrderServiceMDB` and `InventoryNotificationMDB` behaviors
- Keycloak JS adapter deprecation: front-end login flow must be re-verified against OIDC
- WebLogic JMS client code (`t3://localhost:7001`) is dead/broken in the EAP deployment — decide removal vs. reimplementation
- Security findings from graph analysis: 3 potential secrets and 1 taint flow all in vendored `bower_components` JS (false positives for the Java app, but the vendored tree ships known-old AngularJS/jQuery — note for future UI modernization); database credentials appear in README runbook — move to Secrets
- Team unfamiliarity with Quarkus/Reactive Messaging — include training and pairing in Phase 1

---

**Request**: Create a comprehensive migration plan following the above requirements. The plan should include specification, design, task breakdown, and user stories suitable for immediate execution by the development team.
