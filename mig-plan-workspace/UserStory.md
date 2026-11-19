# User Stories

CoolStore Monolith migration: Java EE 7 / JBoss EAP 7.4 → Quarkus 3 / Java 21 on OpenShift.
Task references point to task groups in `tasks.md`. Specification and design: `../mig-prompt-workspace/migration-prompt.md`.

## User Story 1: Modernized Build Foundation on Quarkus 3

**As a:** Platform Engineer
**I want:** The CoolStore monolith restructured onto a Quarkus 3 / Java 21 Maven build with the audit-logging-library upgraded to 2.0.0
**So that:** All subsequent migration work happens on a cloud-native, OpenShift-ready foundation and the mandatory Konveyor rules are satisfied from the start

---

### Tasks
- ## 1. Foundation & Build Modernization (tasks.md)

### Acceptance Criteria
- [x] `mvn package` produces a Quarkus fast-jar with Java 21; no `javaee-api`/EAP dependencies remain in `pom.xml`
- [x] `audit-logging-library` 2.0.0 is used; `OrderService` compiles against `StreamableAuditLogger` + `AuditEvent` record + `logEventAsync`
- [x] All 5 rules in `rules/rule.yaml` (audit-logging-0001..0005) pass analysis with no findings
- [x] CI pipeline builds and tests the project on every commit

### Details
- **Priority:** High — blocks all other stories
- **Estimate:** 8 story points

### Preconditions
- Team has Java 21 + Maven + Podman locally
- `lib/audit-logging-library-2.0.0.jar` available (present in repo)

### Steps to Reproduce / Implementation Notes
1. Restructure pom (BOM, plugin, extensions, vendored JAR)
2. Rework `OrderService` audit-logging per rules
3. Scaffold `application.properties` with env-var placeholders
4. Wire CI

### Dependencies
- None (first story)

### Test Cases
- Test 1: Pricing/promotion characterization tests captured and passing on baseline — reference mig-test-gen output from task 1.8
- Test 2: Audit event emitted asynchronously (mock TCP sink)

### Notes / Comments
- Characterization tests must be written against the EAP baseline BEFORE changing behavior (task 1.8 runs first)

---

## User Story 2: WebLogic Decoupling

**As a:** Application Developer
**I want:** All WebLogic-specific shims, lifecycle listeners, and the WebLogic JMS client removed
**So that:** The application has no proprietary application-server coupling and can run on Quarkus

---

### Tasks
- ## 2. Remove WebLogic-Specific Code (tasks.md)

### Acceptance Criteria
- [x] No `weblogic.*` imports or in-repo `src/main/java/weblogic/` classes remain (verified via rgctl/rg search)
- [x] `StartupListener` behavior preserved via a Quarkus `StartupEvent` observer
- [x] Low-inventory logic from `InventoryNotificationMDB` captured for re-implementation (story 5)

### Details
- **Priority:** High — rgctl migration schedule puts `weblogic.application` first (step 9)
- **Estimate:** 3 story points

### Preconditions
- Story 1 complete (Quarkus build working)

### Steps to Reproduce / Implementation Notes
1. Delete shim classes; convert `StartupListener`
2. Strip WebLogic JMS client from `InventoryNotificationMDB`
3. Re-run `rgctl discover .` and verify zero weblogic references in graph

### Dependencies
- User Story 1

### Test Cases
- Test 1: Quarkus startup observer fires (log assertion in `@QuarkusTest`)

### Notes / Comments
- The WebLogic JMS path (`t3://localhost:7001`) was already non-functional on EAP — removal is safe

---

## User Story 3: Persistence and Domain Migration

**As a:** Application Developer
**I want:** The JPA entities and Flyway database bootstrap migrated to Quarkus Hibernate ORM and quarkus-flyway
**So that:** The catalog, inventory, and order data work unchanged against PostgreSQL on OpenShift

---

### Tasks
- ## 3. Migrate Persistence & Domain Model (tasks.md)

### Acceptance Criteria
- [x] Entities use `jakarta.persistence`; table names `PRODUCT_CATALOG`, `INVENTORY`, `ORDERS`, `ORDER_ITEMS` unchanged
- [x] `quarkus-flyway` runs `V1_1__CreateSchema.sql`/`V1_2__AddInitialData.sql` on startup; existing DB baselined
- [x] `DataBaseMigrationStartup` and `persistence/Resources.java` removed; `EntityManager` injected directly
- [x] Persistence tests green against Testcontainers PostgreSQL

### Details
- **Priority:** High — rgctl schedule: `coolstore.model` step 17, `coolstore.persistence` step 55
- **Estimate:** 5 story points

### Preconditions
- Story 1 complete; PostgreSQL available for tests

### Steps to Reproduce / Implementation Notes
1. Namespace swap on entities; JSON-B annotations on POJOs
2. Datasource config via env vars; remove persistence.xml
3. Flyway bootstrap; delete old startup bean

### Dependencies
- User Story 1

### Test Cases
- Test 1: Order + eager items persisted and reloaded
- Test 2: Inventory merge updates quantity

---

## User Story 4: Business Services on CDI

**As a:** Application Developer
**I want:** The EJB services converted to CDI beans with the remote-EJB self-lookup eliminated
**So that:** Business logic runs on Quarkus DI with the same pricing, checkout, and audit behavior

---

### Tasks
- ## 4. Migrate Business Services — EJB → CDI (tasks.md)

### Acceptance Criteria
- [x] `@Stateful`/`@Stateless`/`@Singleton` replaced by CDI scopes; no `javax.ejb` imports remain
- [x] `ShippingService` injected directly (no JNDI `ejb:/ROOT/...` lookup); remote interface collapsed
- [x] Cart state keyed by cartId; behavior documented for horizontal scaling
- [x] `Transformers` order JSON contract byte-compatible with characterization baseline
- [x] Unit coverage ≥80% on services

### Details
- **Priority:** High — `coolstore.service` is the largest Java community in the graph
- **Estimate:** 8 story points

### Preconditions
- Stories 1–3 complete

### Steps to Reproduce / Implementation Notes
1. Scope conversion per class (task 4.1–4.4)
2. Remove JNDI lookup; inject `ShippingService`
3. Replace `javax.json` in `Transformers` preserving contract

### Dependencies
- User Stories 1–3

### Test Cases
- Test 1: Full pricing matrix (shipping tiers, promos, insurance) — characterization parity
- Test 2: Checkout resets cart and prices empty cart

### Notes / Comments
- Risk R1 (in-memory cart) accepted for phase 1: per-cartId in-memory map; externalization tracked for later

---

## User Story 5: Reactive Messaging Order Flow

**As a:** Application Developer
**I want:** The JMS topic flow replaced by Quarkus reactive messaging over AMQP
**So that:** Checkout orders are processed asynchronously with both consumers (order persistence and inventory alerts) preserved

---

### Tasks
- ## 5. Migrate Messaging — JMS → Reactive Messaging (tasks.md)

### Acceptance Criteria
- [x] Checkout publishes order JSON to the orders channel (`MutinyEmitter`)
- [x] `@Incoming` consumer persists order and decrements inventory (OrderServiceMDB semantics)
- [x] Second consumer raises low-inventory alert at threshold 50 (InventoryNotificationMDB semantics)
- [ ] Broker deployed on OpenShift; app↔broker wiring via Secret

### Details
- **Priority:** High
- **Estimate:** 8 story points

### Preconditions
- Stories 3–4 complete (persistence + services)

### Steps to Reproduce / Implementation Notes
1. Configure `quarkus-messaging-amqp`
2. Convert producer + both consumers
3. Integration tests with broker container

### Dependencies
- User Stories 3, 4

### Test Cases
- Test 1: Publish → both consumers receive (fan-out)
- Test 2: Order persisted + inventory decremented + alert at threshold

---

## User Story 6: REST API and Web Tier on Quarkus

**As a:** Storefront User
**I want:** The same CoolStore web UI and REST API served by Quarkus with OIDC login
**So that:** I can browse, manage a cart, and check out exactly as before, authenticated via Keycloak OIDC

---

### Tasks
- ## 6. Migrate REST API & Web Tier (tasks.md)

### Acceptance Criteria
- [x] All 9 REST operations available under `/services` with identical contracts (characterization suite green)
- [x] `index.jsp`/`health.jsp` converted to static resources; health via `/q/health`
- [x] AngularJS app served as static assets from the container
- [x] OIDC authentication via `quarkus-oidc` + Keycloak realm `eap`; secured checkout requires login
- [x] Unauthenticated access to secured endpoints returns 401

### Details
- **Priority:** High — user-visible surface
- **Estimate:** 8 story points

### Preconditions
- Stories 3–5 complete; Keycloak deployed (may land with story 7)

### Steps to Reproduce / Implementation Notes
1. JAX-RS → quarkus-rest namespace swap; scope fix for `CartEndpoint`
2. Static assets relocation
3. OIDC config + front-end login flow

### Dependencies
- User Stories 3–5

### Test Cases
- Test 1: Contract tests for all 9 operations
- Test 2: OIDC login → checkout succeeds; anonymous checkout blocked

---

## User Story 7: Containerized Deployment on OpenShift

**As a:** Platform Engineer
**I want:** The Quarkus application containerized and deployed to OpenShift with its database, Keycloak, and broker
**So that:** CoolStore runs as a managed, scalable, HA cloud workload replacing the EAP cluster

---

### Tasks
- ## 7. Containerization & OpenShift Deployment (tasks.md)

### Acceptance Criteria
- [ ] Multi-stage UBI9/OpenJDK-21 image builds in CI and pushes to the internal registry; scan shows no critical CVEs
- [x] Deployment runs ≥2 replicas with liveness/readiness/startup probes, resource limits, and HPA
- [x] Secrets hold DB credentials, Keycloak client secret, audit TCP endpoint; ConfigMaps hold app config
- [x] pom.xml OpenShift profile filled in (TODO resolved)
- [ ] Smoke suite green against the Route

### Details
- **Priority:** High
- **Estimate:** 8 story points

### Preconditions
- Stories 1–6 complete

### Steps to Reproduce / Implementation Notes
1. Finalize Containerfile; manifests; supporting services
2. Wire secrets/config; deploy; smoke test

### Dependencies
- User Stories 1–6

### Test Cases
- Test 1: `/q/health/ready` green post-deploy
- Test 2: Authenticated checkout end-to-end through the Route

---

## User Story 8: Verified Cutover from EAP

**As a:** Operations Lead
**I want:** A verified parallel run and controlled cutover from JBoss EAP to the OpenShift deployment
**So that:** The business accepts the new platform with zero functional regressions and a safe rollback path

---

### Tasks
- ## 8. Verification, Parallel Run & Cutover (tasks.md)

### Acceptance Criteria
- [x] Characterization suite passes against the OpenShift deployment (contract parity with EAP baseline)
- [x] Performance report: startup, memory, checkout throughput vs. baseline
- [x] All Konveyor rules pass; no `javax.*`/`weblogic.*` imports; image scan clean
- [ ] 2-week stable parallel run completed before cutover
- [ ] EAP decommissioned after 30-day rollback window; runbook and final report delivered

### Details
- **Priority:** High — closes the migration
- **Estimate:** 5 story points

### Preconditions
- Stories 1–7 complete; monitoring wired to OpenShift observability

### Steps to Reproduce / Implementation Notes
1. Run verification suites; performance tests
2. Parallel run and monitoring
3. Cutover; decommission

### Dependencies
- User Stories 1–7

### Test Cases
- Test 1: Full characterization suite green in staging and production-like environment
- Test 2: Cutover rehearsal: traffic switch with rollback executed successfully

### Notes / Comments
- Schema is unchanged, so rollback = redeploy EAP WAR against the same PostgreSQL
