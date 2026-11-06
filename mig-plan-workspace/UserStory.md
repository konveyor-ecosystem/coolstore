# User Stories

CoolStore Monolith → Quarkus 3 / Java 21 / OpenShift. Stories reference task groups in `mig-plan-workspace/tasks.md`; requirements trace to `mig-prompt-workspace/migration-prompt.md` and the rgctl knowledge graph.

## User Story 1: Quarkus Foundation with Verified Baseline

**As a:** Platform Engineer
**I want:** A Quarkus 3 / Java 21 build pipeline replacing the Java EE 7 WAR build, plus characterization tests locking in current behavior
**So that:** All later migration work compiles and ships through CI, and we can prove behavior parity against the legacy JBoss application

---

### Tasks
- ## 1. Quarkus 3 Foundation & Build Pipeline
- ## 2. Characterization Test Baseline (before code changes)

### Acceptance Criteria
- [ ] `mvn package` produces a Quarkus fast-jar built with Java 21 (no WAR, no `javaee-api` deps)
- [ ] Build depends on `audit-logging-library:2.0.0` from a proper repository (no `systemPath`)
- [ ] CI runs on every PR: build + tests + image build + scan (tests no longer skipped)
- [ ] Characterization suite captures REST contracts (cart/products/orders), JMS message contract, and checkout side effects, and passes against the legacy JBoss app
- [ ] Performance baseline (startup, memory, throughput) recorded

### Details
- **Priority:** High — everything depends on this
- **Estimate:** 8 story points

### Preconditions
- OpenShift cluster + internal registry access
- Legacy stack runnable (JBoss EAP 7.4, PostgreSQL, Keycloak per README)

### Steps to Reproduce / Implementation Notes
1. Convert pom (TG1.1–1.4), add CI (TG1.6), keep `openshift` profile placeholder for TG13
2. Stand up legacy stack and record baseline + contracts (TG2)
3. Validate CI gates on a trivial change

### Dependencies
- None (first story)

### Test Cases
- Test 1: CI fails when compile breaks — expected: pipeline red
- Test 2: Contract tests pass against legacy app — expected: all green, contracts frozen

### Notes / Comments
- `javax.*` → `jakarta.*` compile errors are expected until TG4–TG9 land; gate merge on staged compilation, not full green

---

## User Story 2: Data and Model Layer Migration

**As a:** Application Developer
**I want:** The PostgreSQL datasource, Flyway migrations, and all 8 JPA entities running on Quarkus Hibernate ORM
**So that:** Data access works identically on the new stack with zero schema/data disruption

---

### Tasks
- ## 3. Data Foundation — PostgreSQL, Hibernate ORM, Flyway, Persistence Producer
- ## 4. Model Layer Migration (`com.redhat.coolstore.model`)

### Acceptance Criteria
- [ ] App boots against existing PostgreSQL via `quarkus.datasource.*` (no JBoss datasource/JNDI, no `module.xml`)
- [ ] Flyway `migrate-at-start` applies `V1_1`/`V1_2` history-preserving; no schema regeneration
- [ ] All entities use `jakarta.persistence`; mappings verified against `V1_1__CreateSchema.sql`
- [ ] `@QuarkusTest` with Testcontainers PostgreSQL passes (boot + Flyway + entity round-trip)

### Details
- **Priority:** High — blocks TG5–TG9
- **Estimate:** 5 story points

### Preconditions
- User Story 1 complete
- PostgreSQL database available for dev/test

### Steps to Reproduce / Implementation Notes
1. Configure datasource + Flyway (TG3.1–3.3), remove `Resources.java` producer (TG3.4)
2. Migrate model namespace (TG4.1–4.2), keep JSON/field shapes unchanged
3. Validate against real DB (TG3.5)

### Dependencies
- User Story 1

### Test Cases
- Test 1: Startup with existing DB — expected: Flyway no-op, entities load
- Test 2: Entity persist/find round-trip — expected: identical data to legacy

### Notes / Comments
- Follows rgctl migration order (`model` first among app packages)

---

## User Story 3: Services, Utils, and JMS Order Flow

**As a:** Application Developer
**I want:** EJB services, WebLogic legacy code, and the `topic/orders` messaging flow migrated to CDI and Quarkus messaging
**So that:** Business logic runs without an EJB container and the async order flow is preserved on Artemis

---

### Tasks
- ## 5. Utils Layer & WebLogic Legacy Cleanup
- ## 6. Service Layer Migration — EJB → CDI
- ## 7. JMS Order Flow Migration (`topic/orders`)

### Acceptance Criteria
- [ ] `@Stateless`/`@Stateful`/`@Singleton` beans replaced by CDI scopes + `@Transactional`; no `javax.ejb` imports remain
- [ ] `ShoppingCartService` cart state works per-`cartId` without `@Stateful` session affinity (strategy documented)
- [ ] WebLogic shims (`weblogic.*` classes), `StartupListener` WebLogic inheritance, and `InventoryNotificationMDB` (t3:// JNDI) removed; lifecycle via CDI `StartupEvent`
- [ ] Checkout publishes cart JSON to `topic/orders` (Artemis); consumer persists order + updates inventory with auto-acknowledge semantics
- [ ] No `InitialContext`/JNDI lookups remain in source

### Details
- **Priority:** High — core business logic
- **Estimate:** 13 story points

### Preconditions
- User Story 2 complete (EntityManager available)

### Steps to Reproduce / Implementation Notes
1. Migrate utils + delete shims (TG5)
2. Convert services incl. cart-state decision (TG6.1–6.4)
3. Rebuild producer/consumer on Quarkus messaging (TG7.1–7.3), wire broker (TG7.4)

### Dependencies
- User Story 2

### Test Cases
- Test 1: Publish cart JSON → order persisted + inventory decremented — expected: matches TG2.3 contract
- Test 2: Two concurrent carts per user — expected: independent state
- Test 3: Startup log shows lifecycle events — expected: no WebLogic classes on classpath

### Notes / Comments
- rgctl flagged 317 circular dependencies — triage the worst in `service` package during TG6
- `Transformers` JSON format must stay byte-compatible (frontend + JMS)

---

## User Story 4: Audit Logging v2 Upgrade

**As a:** Security Engineer
**I want:** Order audit events emitted via `audit-logging-library:2.0.0` using async TCP streaming
**So that:** The mandatory `audit-logging-migration` ruleset is satisfied and audit events are reliable and non-blocking on Java 21

---

### Tasks
- ## 8. Audit Logging Library v2 Upgrade (mandatory ruleset `rules/rule.yaml`)

### Acceptance Criteria
- [ ] All five mandatory rules green: dependency 2.0.0 (0001), no `AuditEvent.builder()` (0002), no `FileSystemAuditLogger` (0003), no sync `logEvent` (0004), no `logSuccess`/`logFailure` (0005)
- [ ] `OrderService` uses `StreamableAuditLogger` over TCP with endpoint from config/Secret
- [ ] Audit events emitted asynchronously on order save; no local `device-inventory-audit-logs` directory
- [ ] Tests assert an `AuditEvent` record reaches a TCP stub listener

### Details
- **Priority:** High — mandatory gate for go-live
- **Estimate:** 5 story points

### Preconditions
- User Story 3 complete (OrderService migrated to CDI)

### Steps to Reproduce / Implementation Notes
1. Rework `OrderService` init/cleanup around v2 API (TG8.6)
2. Apply record instantiation + `logEventAsync` (TG8.2–8.5)
3. Verify with TCP stub test (TG8.7)

### Dependencies
- User Story 3

### Test Cases
- Test 1: Order save → AuditEvent received on TCP stub — expected: async, non-blocking
- Test 2: Static check for forbidden v1 patterns — expected: zero matches

### Notes / Comments
- v2 JAR is already vendored in `lib/audit-logging-library-2.0.0.jar`
- Audit TCP endpoint must exist in every environment before cutover

---

## User Story 5: REST API and Frontend Serving

**As a:** Storefront User
**I want:** The same AngularJS storefront and REST API served by Quarkus
**So that:** My browsing and cart experience is unchanged after migration

---

### Tasks
- ## 9. REST Layer & Cart State (`com.redhat.coolstore.rest`)
- ## 11. Frontend & Static Resources

### Acceptance Criteria
- [ ] All REST paths (`/cart`, `/orders`, `/products`) preserved with identical JSON contracts (TG2.2 suite green against Quarkus)
- [ ] Cart add/set/delete/checkout work per-`cartId` without HTTP session affinity
- [ ] SPA served from `META-INF/resources` (static `index.html`, no JSP); `coolstore.json` honored
- [ ] `/q/health` probes replace `health.jsp`

### Details
- **Priority:** High — user-visible surface
- **Estimate:** 8 story points

### Preconditions
- User Story 3 complete (services + cart strategy)

### Steps to Reproduce / Implementation Notes
1. Migrate endpoints to `jakarta.ws.rs` + quarkus-rest (TG9.1–9.3)
2. Move static assets, convert `index.jsp` (TG11.1–11.3)
3. Run characterization suite against Quarkus app

### Dependencies
- User Stories 3 (and 2 for entities)

### Test Cases
- Test 1: TG2.2 contract suite against Quarkus — expected: green
- Test 2: `/`, `/app/...`, `/coolstore.json` return 200 — expected: static serving works

### Notes / Comments
- No frontend framework rewrite in scope — AngularJS SPA preserved as-is

---

## User Story 6: Keycloak OIDC Security

**As a:** Storefront User
**I want:** To sign in with my existing Keycloak credentials and check out securely
**So that:** Access control works the same as before, now enforced server-side

---

### Tasks
- ## 10. Security — Keycloak OIDC

### Acceptance Criteria
- [ ] `quarkus-oidc` validates tokens for realm `eap` / client `eap-app` (imported from `realm-export.json`)
- [ ] Checkout/order endpoints require Bearer auth; catalog browsing stays public
- [ ] SPA sign-in flow works through the OpenShift Route
- [ ] All 8 rgctl-flagged potential secrets moved to OpenShift Secrets/ConfigMaps

### Details
- **Priority:** High — required for production
- **Estimate:** 5 story points

### Preconditions
- User Story 5 complete (endpoints exist)
- Keycloak reachable from cluster

### Steps to Reproduce / Implementation Notes
1. Configure quarkus-oidc (TG10.1–10.2)
2. Adapt SPA login (TG10.3), externalize secrets (TG10.4)

### Dependencies
- User Story 5

### Test Cases
- Test 1: Unauthenticated checkout → 401
- Test 2: Valid Keycloak token → checkout succeeds (E2E sign-in journey)

### Notes / Comments
- Switch from client-side to server-side validation changes token handling — verify SPA carefully

---

## User Story 7: Containerization

**As a:** DevOps Engineer
**I want:** A hardened, scanned container image of the Quarkus app
**So that:** The application can be deployed consistently on OpenShift

---

### Tasks
- ## 12. Containerization

### Acceptance Criteria
- [ ] Multi-stage Containerfile builds fast-jar image on UBI/JDK 21, running as non-root
- [ ] Image pushed to internal registry by CI; scan shows no High/Critical findings
- [ ] Local Podman run passes smoke: boots, `/q/health/ready` green, cart→checkout journey works against dev dependencies

### Details
- **Priority:** Medium — after app migration, before deployment
- **Estimate:** 5 story points

### Preconditions
- User Stories 1–6 complete (image contains the migrated app)

### Steps to Reproduce / Implementation Notes
1. Write Containerfile (TG12.1), wire registry + scanning (TG12.2)
2. Local verification with Podman (TG12.3)

### Dependencies
- User Stories 1–6

### Test Cases
- Test 1: Container smoke test — expected: health green + journey passes
- Test 2: Image scan — expected: no High/Critical

### Notes / Comments
- Native compilation is an optional stretch goal, not a gate

---

## User Story 8: OpenShift Deployment

**As a:** Platform Engineer
**I want:** The app running on OpenShift with ≥2 replicas, probes, secrets, and supporting services
**So that:** We replace the 2-node JBoss cluster with a resilient, rolling-updatable cloud deployment

---

### Tasks
- ## 13. OpenShift Deployment

### Acceptance Criteria
- [ ] Deployment (≥2 replicas) + Service + Route live; rolling updates and rollback demonstrated
- [ ] Liveness/readiness probes on `/q/health`; resource requests/limits set
- [ ] PostgreSQL, Keycloak (realm `eap`), AMQ Broker (`topic/orders`) deployed and wired via ConfigMaps/Secrets
- [ ] `openshift` Maven profile (pom TODO) works end-to-end
- [ ] Multi-replica validation: cart state consistent, JMS consumed across pods, zero-downtime rolling update

### Details
- **Priority:** High — production readiness
- **Estimate:** 8 story points

### Preconditions
- User Story 7 complete (image exists)
- Cluster + namespace access

### Steps to Reproduce / Implementation Notes
1. Author manifests + config/secrets (TG13.1–13.3)
2. Deploy dependencies and app (TG13.4–13.5)
3. Multi-replica + rolling update validation (TG13.6)

### Dependencies
- User Story 7

### Test Cases
- Test 1: Kill a pod during load — expected: no user-visible errors
- Test 2: Rolling update — expected: zero downtime, probes gate traffic

### Notes / Comments
- `<distributable/>` session-distribution intent is re-achieved via TG6.2 cart-state strategy

---

## User Story 9: Verification, Performance & Cutover

**As a:** Product Owner
**I want:** The migrated app fully verified, monitored, and cut over from JBoss with a rollback path
**So that:** Customers experience no regression and we can retire the legacy platform

---

### Tasks
- ## 14. Verification, Performance & Cutover

### Acceptance Criteria
- [ ] E2E journey green on OpenShift: browse → cart → sign-in → checkout → order persisted → inventory decremented → async audit events streamed
- [ ] Performance parity or better vs. TG2 baseline (startup, memory, throughput) — results published
- [ ] Metrics (`/q/metrics`) in OpenShift monitoring with dashboards + alerts
- [ ] Canary → 48h monitoring → full cutover executed; JBoss rollback path retained 30 days
- [ ] JBoss decommissioned; runbook, training, and close-out report delivered

### Details
- **Priority:** High — final gate
- **Estimate:** 8 story points

### Preconditions
- User Story 8 complete

### Steps to Reproduce / Implementation Notes
1. Run E2E + performance suites (TG14.1–14.2)
2. Enable observability (TG14.3)
3. Canary, monitor, cutover, decommission (TG14.4–14.5), train + document (TG14.6)

### Dependencies
- User Story 8

### Test Cases
- Test 1: Full E2E suite — expected: green
- Test 2: Load test vs. baseline — expected: parity or improvement

### Notes / Comments
- Success criteria checklist from `migration-prompt.md` is the acceptance report template
