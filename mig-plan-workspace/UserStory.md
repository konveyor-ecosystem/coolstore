# User Stories: coolstore → Quarkus 3.15 / Java 21 / OpenShift

Traceability: stories → `tasks.md` task groups → `mig-prompt-workspace/migration-prompt.md` design decisions → rgctl index (`.rgctl/`, migration_plan.json order).

## User Story 1: Modern Runtime Foundation

**As a:** Platform Engineer
**I want:** The app to build on Quarkus 3.15 LTS and Java 21, with its database managed by Quarkus Flyway
**So that:** We run on a supported, cloud-native runtime without JBoss EAP

---

### Tasks
- ## Task Group 1: Build Foundation (tasks.md)
- ## Task Group 2: Persistence, Datasource & Flyway (tasks.md)

### Acceptance Criteria
- [ ] `mvn -B verify` succeeds on JDK 21 with the Quarkus 3.15.x BOM; no `javax.*` imports remain
- [ ] audit-logging-library 2.0.0 is resolved without `system` scope; the 1.0.0 jar is removed (rule audit-logging-0001)
- [ ] Flyway migrates V1_1/V1_2 at startup; the datasource is configured only through DB_* env vars
- [ ] `web.xml`/`<distributable/>` and `persistence.xml` are removed

---

### Details
- **Priority:** High
- **Estimate:** 5 story points

### Preconditions
- JDK 21 + Maven are available (installed into /tmp for verification)

### Steps to Reproduce / Implementation Notes
1. TG1 pom rewrite and jakarta rename → 2. TG2 datasource/Flyway → 3. boot test

### Dependencies
- None (first story)

### Test Cases
- Test 1: Quarkus context boots in the test profile (H2) - expected: starts, Flyway applied
- Test 2: Catalog seed query - expected: item `329299` exists with inventory

### Notes / Comments
- Pin the Hibernate 6 ID generator to `hibernate_sequence` to avoid sequence mismatches

---

## User Story 2: Same Shopping Experience on Stateless Services

**As a:** Shopper
**I want:** Browsing, cart, pricing and checkout to behave exactly as before
**So that:** The migration is invisible to me while the backend becomes cloud-friendly

---

### Tasks
- ## Task Group 3: Service Layer EJB → CDI, Local Shipping, Stateless Cart
- ## Task Group 4: Messaging JMS → SmallRye Reactive Messaging
- ## Task Group 6: REST Layer & OIDC Security

### Acceptance Criteria
- [ ] All 9 `/services/*` endpoints keep their paths, verbs and JSON field names (REST Assured contract tests)
- [ ] Shipping tiers, insurance, 25% promo on 329299 and free shipping at ≥ 75 are unchanged (characterization tests)
- [ ] Carts are keyed by cartId in an application-scoped store; no HTTP session or `@Stateful`/`@SessionScoped` remains (Konveyor session-00000 resolved)
- [ ] Checkout publishes to the `orders` channel; the order is persisted and inventory decremented; the low-stock warning is logged
- [ ] No EJB, JMS, JNDI or remote-EJB lookup remains; ShippingService is a local CDI bean
- [ ] quarkus-oidc is configured for realm `eap` (enabled via `OIDC_ENABLED`); anonymous API access stays as today

---

### Details
- **Priority:** High
- **Estimate:** 8 story points

### Preconditions
- User Story 1 is done

### Steps to Reproduce / Implementation Notes
1. TG3 services → 2. TG4 messaging → 3. TG6 REST + OIDC → 4. run the contract suite

### Dependencies
- User Story 1

### Test Cases
- Test 1: POST /services/cart/c1/329299/1 - expected: price discounted 25%, promoSavings negative
- Test 2: POST /services/cart/checkout/c1 - expected: empty cart returned; GET /services/orders includes the new order
- Test 3: carts c1 and c2 are independent - expected: no cross-contamination

### Notes / Comments
- In-memory cart and messaging: replicas=1/sticky until a Data Grid/Redis CartStore and AMQP/Kafka exist (config-only switch)

---

## User Story 3: Compliant Streaming Audit Trail

**As a:** Compliance Officer
**I want:** Order events streamed to the central audit collector over TCP with the 2.0.0 library
**So that:** Audit records are centralized instead of written to ephemeral container disks

---

### Tasks
- ## Task Group 5: Audit Logging 2.0.0

### Acceptance Criteria
- [ ] `StreamableAuditLogger` (TCP, host/port from AUDIT_STREAM_HOST/PORT) replaces `FileSystemAuditLogger` (rule 0003)
- [ ] Events are built with the `AuditEvent` record constructor and sent via `logEventAsync` (rules 0002, 0004)
- [ ] No `builder()`, `logEvent`, `logSuccess` or `logFailure` calls remain (rule 0005)
- [ ] The app starts and orders save when no collector is reachable

---

### Details
- **Priority:** High
- **Estimate:** 3 story points

### Preconditions
- User Story 1 (Java 21, lib 2.0.0 resolvable)

### Steps to Reproduce / Implementation Notes
1. CDI producer for the logger → 2. emit ORDER_CREATED on save → 3. socket test

### Dependencies
- User Story 1; parallel with User Story 2 (it touches OrderService)

### Test Cases
- Test 1: embedded ServerSocket - expected: JSON with `event_type":"ORDER_CREATED"` received
- Test 2: no server - expected: order persisted, warning logged

### Notes / Comments
- The library depends on Jackson 2.13 / slf4j 1.7; the Quarkus BOM manages the versions

---

## User Story 4: Static UI and Legacy Cleanup

**As a:** Developer
**I want:** The AngularJS UI served as Quarkus static resources and all WebLogic/EAP leftovers removed
**So that:** The codebase is a single clean Quarkus app with no dead vendor stubs

---

### Tasks
- ## Task Group 7: UI Static Resources & WebLogic/EAP Cleanup

### Acceptance Criteria
- [ ] `GET /` serves `index.html` (formerly index.jsp); `/app/**`, `/partials/**` and `/coolstore.json` load
- [ ] `src/main/java/weblogic/**` is deleted; StartupListener observes StartupEvent/ShutdownEvent
- [ ] The grep gate finds no `weblogic`, `InitialContext`, `javax.` or `<scope>system`

---

### Details
- **Priority:** Medium
- **Estimate:** 3 story points

### Preconditions
- User Story 2 (REST stable)

### Steps to Reproduce / Implementation Notes
1. Move webapp → META-INF/resources → 2. JSP → HTML → 3. delete stubs → 4. grep gate

### Dependencies
- User Story 2

### Test Cases
- Test 1: GET / - expected: 200 with `ng-app`
- Test 2: GET /q/health/ready - expected: UP

### Notes / Comments
- `health.jsp` is replaced by SmallRye Health

---

## User Story 5: Verified, Containerized, OpenShift-Ready Delivery

**As a:** Release Manager
**I want:** A green test suite, a UBI-based container image and OpenShift manifests with a runbook
**So that:** The migrated app can be deployed, rolled back and operated safely on OpenShift

---

### Tasks
- ## Task Group 8: Test Suite Consolidation & Quality Gates
- ## Task Group 9: Containerization
- ## Task Group 10: OpenShift Deployment Manifests & Runbook

### Acceptance Criteria
- [ ] `mvn verify` is green; JaCoCo ≥ 70% line coverage on `com.redhat.coolstore`
- [ ] Multi-stage Containerfile (ubi9/openjdk-21 → openjdk-21-runtime), non-root, port 8080, no secrets baked in
- [ ] Kustomize manifests (Deployment+probes, Service, Route, ConfigMap, Secret template, PostgreSQL+PVC, HPA, ImageStream/BuildConfig) parse and validate offline
- [ ] The runbook covers deploy, rollback and smoke test; the README documents all env vars

---

### Details
- **Priority:** High
- **Estimate:** 5 story points

### Preconditions
- User Stories 1–4 complete

### Steps to Reproduce / Implementation Notes
1. TG8 tests/coverage → 2. TG9 Containerfile → 3. TG10 manifests + runbook

### Dependencies
- User Stories 1–4; podman/oc are unavailable here, so validation is static

### Test Cases
- Test 1: smoke.sh against the local jar - expected: browse → add → checkout → order listed
- Test 2: manifest lint - expected: all YAML valid, probes point to /q/health/*

### Notes / Comments
- Rollback: keep the EAP deployment and source branch until cutover; `oc rollout undo`
