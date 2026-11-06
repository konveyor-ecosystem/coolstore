# User Stories

**Project:** CoolStore Monolith — Java EE 7 / JBoss EAP 7.4 → Quarkus 3 / Java 21 on Red Hat OpenShift

**Traceability:** Stories → task groups in `mig-plan-workspace/tasks.md` → specification in `mig-prompt-workspace/migration-prompt.md` → rgctl knowledge graph (`.rgctl/`).

---

## User Story 1: Modern Runtime Foundation

**As a:** Platform Engineer
**I want:** The CoolStore application rebuilt on a Quarkus 3 / Java 21 foundation with a container build and OpenShift CI pipeline
**So that:** We have a modern, container-first runtime that satisfies the mandatory Java 21 requirement and eliminates the legacy Azure deployment pipeline

---

### Tasks
- ## Task Group 1: Foundation — Quarkus 3 Skeleton, Java 21 & Dependency Baseline

### Acceptance Criteria
- [ ] `mvn package` produces a runnable Quarkus JAR with Java 21 toolchain (no WAR, no javaee-api dependencies)
- [ ] audit-logging-library 2.0.0 is a normal Maven dependency (rule `audit-logging-0001` satisfied); `lib/audit-logging-library-1.0.0.jar` deleted
- [ ] All configuration (datasource, broker, OIDC, audit TCP endpoint) is externalized — no secrets in source
- [ ] CI pipeline builds a container image and deploys to OpenShift (Azure workflow removed)
- [ ] App boots; `/q/health/live` returns 200

### Details
- **Priority:** High — everything else depends on this
- **Estimate:** 8 story points

### Preconditions
- Team has JDK 21 and Podman available
- Access to an OpenShift cluster and internal image registry

### Steps to Reproduce / Implementation Notes
1. Execute Task Group 1 subtasks 1.1–1.5 (skeleton, extensions, library upgrade, config, CI)
2. Run mig-test-gen smoke tests (1.6), mig-containerize image (1.7), mig-deploy manifests (1.8)
3. Validate boot + health endpoint

### Dependencies
- None (first story)

### Test Cases
- Test 1: QuarkusTest context boots — `/q/health/live` 200
- Test 2: Dependency tree contains `com.enterprise:audit-logging-library:2.0.0`, no 1.0.0, no `system` scope
- Test 3: CI pipeline produces a scanned image in the registry

### Notes / Comments
- The ruleset (`rules/rule.yaml`) mandates Java 21 via library 2.0.0 — this is the hard constraint driving the toolchain choice

---

## User Story 2: Reliable Data Layer

**As a:** Application Developer
**I want:** The JPA entities, Hibernate ORM, and Flyway migrations running on Quarkus against the existing PostgreSQL database
**So that:** Product catalog, inventory, and order data are preserved with zero data migration and schema-compatible persistence

---

### Tasks
- ## Task Group 2: Model & Persistence Layer

### Acceptance Criteria
- [ ] All 8 model classes compile under `jakarta.*` namespace; JAXB `@XmlRootElement` removed
- [ ] `quarkus-hibernate-orm` replaces `persistence.xml` JTA datasource config
- [ ] `quarkus-flyway` runs `V1_1__CreateSchema.sql` and `V1_2__AddInitialData.sql` at startup; `DataBaseMigrationStartup` deleted
- [ ] Entity integration tests pass against Testcontainers PostgreSQL with the existing schema
- [ ] PostgreSQL deployable on OpenShift with Secret-based credentials

### Details
- **Priority:** High — service layer depends on entities
- **Estimate:** 5 story points

### Preconditions
- User Story 1 complete (Quarkus skeleton, extensions)
- Existing PostgreSQL data accessible for verification

### Steps to Reproduce / Implementation Notes
1. Migrate entities (2.1), configure ORM (2.2) and Flyway (2.3)
2. Replace EntityManager producer with Quarkus-managed injection + `@Transactional` (2.4)
3. Verify mappings against schema (2.5); run test-gen (2.6), containerize/deploy PostgreSQL (2.7–2.8)

### Dependencies
- User Story 1

### Test Cases
- Test 1: Flyway migrate-at-start is idempotent on an already-migrated database
- Test 2: Catalog CRUD via `CatalogService` persists and merges correctly
- Test 3: `Order`/`OrderItem` persist matches ORDERS/ORDER_ITEMS schema

### Notes / Comments
- rgctl community `com.redhat.coolstore.model` (97 members) is the largest app community — migrated first per `migration_plan.json` step 15

---

## User Story 3: Modernized Core Services & Utilities

**As a:** Application Developer
**I want:** The service and utility layers converted from EJB/WebLogic patterns to CDI, Artemis JMS, and audit-logging-library 2.0.0
**So that:** Business logic (pricing, promotions, shipping, orders, audit trail) runs identically on Quarkus with no EJB container, no remote JNDI, and no file-based audit logging

---

### Tasks
- ## Task Group 3: Utils & Cross-Cutting Concerns
- ## Task Group 4: Service Layer — EJB → CDI, JMS & Audit Library 2.0

### Acceptance Criteria
- [ ] All EJBs replaced by CDI beans with correct scopes; cart state is cartId-keyed (multi-tab behavior preserved)
- [ ] Remote EJB JNDI lookup (`ejb:/ROOT/ShippingService!...`) deleted — `ShippingServiceRemote` locally injected
- [ ] Order JSON published to `topic/orders` on Artemis; both consumers (order persistence, inventory notification) receive it
- [ ] `OrderService` uses `StreamableAuditLogger` over TCP with `AuditEvent` records and `logEventAsync` (rules `0002`–`0005` satisfied)
- [ ] Characterization tests pass: 25% promo on item 329299, free shipping ≥ 75, shipping tiers 2.99–10.99, insurance 2%/1.5%/1%, cart dedupe, low-inventory threshold 50
- [ ] Transformers JSON contracts byte-compatible (cart → order JSON round-trip)

### Details
- **Priority:** High — REST layer depends on services
- **Estimate:** 13 story points (largest story; TG3 + TG4)

### Preconditions
- User Stories 1–2 complete
- AMQ Broker available (Testcontainers locally, OpenShift for integration)

### Steps to Reproduce / Implementation Notes
1. Write characterization tests FIRST (4.8) against legacy behavior, then migrate utils (TG3) and services (TG4)
2. Convert cart state design (4.1), remove remote EJB (4.2), migrate JMS producer/consumers (4.3–4.5), migrate audit logging (4.6), convert remaining beans (4.7)
3. Validate on Artemis + PostgreSQL via Testcontainers; containerize/deploy AMQ Broker (4.9–4.10)

### Dependencies
- User Stories 1, 2

### Test Cases
- Test 1: Checkout → `topic/orders` message → order persisted + inventory deducted (integration)
- Test 2: Pricing characterization suite passes unchanged from legacy baseline
- Test 3: Audit event streamed asynchronously over TCP after order save
- Test 4: Low-inventory alert fires below quantity 50

### Notes / Comments
- rgctl edges show `service` depends on `model` (w=37) and `utils` (w=5) — order is deliberate
- `InventoryNotificationMDB` is currently a raw WebLogic JMS client that may be inert; confirm expected behavior before migration (risk noted in migration prompt)

---

## User Story 4: API-Compatible REST Layer

**As a:** Frontend Developer
**I want:** The JAX-RS endpoints migrated to Jakarta REST on Quarkus with identical paths and JSON contracts
**So that:** The AngularJS storefront works against the new backend without any frontend code changes

---

### Tasks
- ## Task Group 5: REST Layer

### Acceptance Criteria
- [ ] `/services/cart`, `/services/products`, `/services/orders` paths preserved exactly (incl. cart sub-paths)
- [ ] `CartEndpoint` is `@RequestScoped`; per-user state handled by cartId-keyed service
- [ ] JSON response shapes identical to legacy (verified by recorded characterization tests)
- [ ] API tests pass against Testcontainers stack

### Details
- **Priority:** High
- **Estimate:** 5 story points

### Preconditions
- User Stories 1–3 complete

### Steps to Reproduce / Implementation Notes
1. Configure `quarkus.rest.path=/services` and migrate the three endpoint classes (5.1–5.3)
2. Verify contracts against frontend service calls `app/services/cart.js`, `app/services/catalog.js` (5.4)
3. Run API characterization tests (5.5); rebuild image and update deployment (5.6–5.7)

### Dependencies
- User Stories 1–3

### Test Cases
- Test 1: `GET /services/products` returns catalog JSON matching legacy shape
- Test 2: Cart add/set/delete/dedupe flows return expected cart state
- Test 3: `GET /services/orders/{id}` returns persisted order

### Notes / Comments
- rgctl community `com.redhat.coolstore.rest` (12 members) depends on service (w=14) and model (w=17) — migrated after both

---

## User Story 5: Seamless Storefront Experience

**As a:** Shopper
**I want:** The CoolStore web storefront served by the new application with unchanged look, behavior, and login
**So that:** My browsing, cart, and checkout experience is identical after the migration

---

### Tasks
- ## Task Group 6: Frontend & Static Assets

### Acceptance Criteria
- [ ] `index.jsp` → static `index.html` served from `META-INF/resources/` at `/`
- [ ] AngularJS app, bower assets, partials, and `keycloak.json` served at unchanged paths
- [ ] `health.jsp` replaced by `/q/health` endpoints
- [ ] E2E storefront flow passes: browse → login → add to cart → checkout

### Details
- **Priority:** Medium
- **Estimate:** 5 story points

### Preconditions
- User Stories 1–4 complete (API compatible)
- Keycloak reachable for login flow (can be local for tests)

### Steps to Reproduce / Implementation Notes
1. Convert JSP and copy static assets preserving paths (6.1–6.2)
2. Swap health endpoint (6.3); run E2E tests (6.4); verify image serves assets (6.5–6.6)

### Dependencies
- User Stories 1–4 (User Story 6 for full login E2E)

### Test Cases
- Test 1: `GET /` returns the storefront HTML with all assets loading (200s)
- Test 2: E2E checkout workflow completes against Keycloak-authenticated session

### Notes / Comments
- rgctl webapp communities (steps 20/21/44/46/89) are mostly vendored bower libraries — only `app/` code is app-owned; no frontend rewrite required

---

## User Story 6: Secure Authentication

**As a:** Security Engineer
**I want:** Keycloak OIDC bearer-token validation protecting the REST API, with Keycloak running on OpenShift
**So that:** Storefront users authenticate via the existing `eap` realm and API access is properly secured in the new platform

---

### Tasks
- ## Task Group 7: Security — Keycloak OIDC Integration

### Acceptance Criteria
- [ ] `quarkus-oidc` validates bearer tokens on `/services/*` (realm `eap`, client `eap-app`)
- [ ] Keycloak deployed on OpenShift with `realm-export.json` imported
- [ ] Frontend keycloak.js login/logout works end-to-end against Quarkus
- [ ] Missing/expired token → 401; valid token → 200 on protected endpoints

### Details
- **Priority:** High
- **Estimate:** 8 story points

### Preconditions
- User Stories 1–5 complete
- TLS certificates for Keycloak Route

### Steps to Reproduce / Implementation Notes
1. Configure quarkus-oidc (7.1); deploy Keycloak with realm import (7.2)
2. Verify keycloak.js adapter flow (7.3); run auth tests (7.4); containerize/deploy Keycloak (7.5–7.6)

### Dependencies
- User Stories 1–5

### Test Cases
- Test 1: Valid Keycloak token accepted on `/services/cart/{cartId}`
- Test 2: Expired/missing token rejected with 401
- Test 3: Login E2E via storefront

### Notes / Comments
- Risk noted in prompt: public client + keycloak.js vs quarkus-oidc validation differences — E2E test against real Keycloak mitigates

---

## User Story 7: Legacy Platform Independence

**As a:** Software Architect
**I want:** All WebLogic and JBoss EAP coupling removed from the codebase with all 5 mandatory migration rules passing
**So that:** The application is fully portable and complies with the repository's mandatory ruleset (`rules/rule.yaml`)

---

### Tasks
- ## Task Group 8: WebLogic Removal & Mandatory Rules Validation

### Acceptance Criteria
- [ ] `weblogic.*` shim packages and `StartupListener` deleted; no `weblogic` references in source
- [ ] `web.xml`, `beans.xml`, and JBoss spec dependencies removed
- [ ] All 5 mandatory rules pass: dependency 2.0.0/Java 21 (`0001`), no `AuditEvent.builder()` (`0002`), no `FileSystemAuditLogger` (`0003`), `logEventAsync` (`0004`), no `logSuccess`/`logFailure` (`0005`)
- [ ] Full regression suite green after cleanup

### Details
- **Priority:** High
- **Estimate:** 3 story points

### Preconditions
- User Stories 1–6 complete (shims unused by then)

### Steps to Reproduce / Implementation Notes
1. Delete shims and StartupListener (8.1–8.2); clean descriptors/deps (8.3)
2. Run rules validation against migrated code (8.4); regression tests (8.5); final image scan (8.6–8.7)

### Dependencies
- User Stories 1–6

### Test Cases
- Test 1: `grep -r weblogic src/` returns nothing
- Test 2: Ruleset validation reports 0 mandatory violations
- Test 3: Full test suite passes post-cleanup

### Notes / Comments
- rgctl steps 33/40 (weblogic communities) and 48 (persistence cleanup) close out here

---

## User Story 8: Production-Ready OpenShift Deployment

**As a:** Operations Lead
**I want:** The full CoolStore stack (app, PostgreSQL, AMQ Broker, Keycloak) deployed on OpenShift with health probes, autoscaling, metrics, and a validated rollback plan
**So that:** The migrated application runs reliably in production with observability and we can safely cut over from the legacy EAP cluster

---

### Tasks
- ## Task Group 9: OpenShift Deployment, Observability & Cutover

### Acceptance Criteria
- [ ] Liveness/readiness/startup probes wired (including datasource, broker, OIDC checks); HPA min 2 replicas; rolling updates with zero unavailable pods
- [ ] Prometheus metrics + JSON logs + dashboards for checkout/order rates and inventory thresholds
- [ ] Parallel run completed; rollback to legacy EAP WAR rehearsed; performance ≥ baseline
- [ ] Traffic cut over to OpenShift; legacy EAP hosts and Azure pipeline decommissioned
- [ ] E2E + load tests pass on-cluster

### Details
- **Priority:** High (cutover story)
- **Estimate:** 8 story points

### Preconditions
- User Stories 1–7 complete
- Production namespace, quotas, and registry access

### Steps to Reproduce / Implementation Notes
1. Configure probes/resources/HPA/observability (9.1–9.3)
2. Deploy full stack (9.4); parallel run + rollback rehearsal (9.5); performance validation (9.6)
3. Cut over traffic and decommission legacy (9.7); production readiness tests + docs (9.8–9.11)

### Dependencies
- User Stories 1–7

### Test Cases
- Test 1: Pod failure → liveness probe restarts; traffic unaffected (rolling update, maxUnavailable 0)
- Test 2: E2E checkout workflow passes on-cluster
- Test 3: Load test meets or beats legacy baseline latency/throughput

### Notes / Comments
- Two-node EAP cluster (`<distributable/>`, standalone-full-ha) is replaced by OpenShift replicas + Artemis topic fan-out
- Keep legacy WAR deployable for the entire migration window (rollback path per migration prompt)
