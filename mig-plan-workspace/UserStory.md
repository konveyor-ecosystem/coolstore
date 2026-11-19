# User Stories

coolstore (APP_ID=7) — Java EE 7 → Quarkus 3.x on Java 21, deployed to Red Hat OpenShift.
Stories reference task groups in `mig-plan-workspace/tasks.md`; specification and design
live in `mig-prompt-workspace/migration-prompt.md`.

## User Story 1: Migration Foundation & Behavior Baseline

**As a:** Platform Engineer
**I want:** A characterization test suite and CI baseline of the current coolstore behavior
**So that:** Every later migration step can be verified against captured current behavior, protecting the checkout revenue path

---

### Tasks
- ## 1. Foundation & Baseline (from tasks.md)

### Acceptance Criteria
- [ ] Characterization tests pass against the current EAP build for services, REST contracts, and the JMS order flow
- [ ] The 2 flagged potential secrets are remediated; the taint flow is documented with a fix plan
- [ ] CI pipeline runs the suite on every commit
- [ ] Baseline metrics (build/startup time, endpoint latency) are recorded

### Details
- **Priority:** High
- **Estimate:** 5 story points

### Preconditions
- Access to the existing EAP 7.4 deployment (or a local reproduction) for baseline capture

### Steps to Reproduce / Implementation Notes
1. Capture endpoint contracts and pricing rules (Task 1.2–1.4)
2. Generate the characterization suite via mig-test-gen (Task 1.7)
3. Wire CI (Task 1.6) and record metrics (Task 1.8)

### Dependencies
- None (entry point)

### Test Cases
- Test 1: Cart pricing rules (promotion/shipping) match captured values — characterization suite green
- Test 2: REST endpoints return captured response shapes
- Test 3: JMS order processing produces captured log output

### Notes / Comments
- Highest-leverage story: everything downstream relies on this safety net

---

## User Story 2: Quarkus Runtime Migration

**As a:** Application Developer
**I want:** The coolstore monolith rebuilt on Quarkus 3.x with Java 21, keeping the existing REST contract
**So that:** The application runs on a supported, cloud-native runtime with fast startup and container-first packaging

---

### Tasks
- ## 2. Quarkus Runtime Migration (Core) (from tasks.md)

### Acceptance Criteria
- [ ] `mvn package` produces a Quarkus fast-jar; application starts with `java -jar` against PostgreSQL
- [ ] All `javax.*` references replaced with `jakarta.*`; zero Java EE provided-scope dependencies remain
- [ ] Characterization suite passes unchanged against the Quarkus build (REST contract preserved)
- [ ] Flyway V1_1/V1_2 apply cleanly; all 8 JPA entities persist correctly
- [ ] Flagged taint flow fixed; circular dependency count reduced

### Details
- **Priority:** High
- **Estimate:** 13 story points

### Preconditions
- User Story 1 complete (characterization suite available)

### Steps to Reproduce / Implementation Notes
1. Restructure the build (Task 2.1), then migrate namespaces/CDI/persistence/REST/services in order (Tasks 2.2–2.7)
2. Generate `@QuarkusTest` integration tests via mig-test-gen (Task 2.9)
3. Iterate until the characterization suite is green

### Dependencies
- User Story 1

### Test Cases
- Test 1: Application boots and `/rest/products` returns the catalog
- Test 2: Add-to-cart and checkout REST flows match characterization captures
- Test 3: Flyway migrations apply on a fresh database; restart is idempotent

### Notes / Comments
- Largest story; split delivery by layer if needed (model/persistence first, then REST/services)

---

## User Story 3: Legacy Decoupling & Library Upgrade

**As a:** Application Developer
**I want:** WebLogic shims, remote EJB, and the system-scoped audit JAR removed
**So that:** The codebase has no legacy server coupling and uses the supported audit-logging-library 2.0.0 on Java 21

---

### Tasks
- ## 3. Legacy Coupling Removal (from tasks.md)

### Acceptance Criteria
- [ ] `src/main/java/weblogic/**` deleted with no compile references remaining
- [ ] `ShippingServiceRemote` RMI coupling replaced by CDI/REST; jboss-rmi dependency removed
- [ ] `audit-logging-library` 2.0.0 consumed as a Maven dependency (no `systemPath`), audit records verified by tests (mandatory ruleset `audit-logging-0001`)
- [ ] Full test suites remain green

### Details
- **Priority:** High
- **Estimate:** 8 story points

### Preconditions
- User Story 2 complete (Quarkus build working)

### Steps to Reproduce / Implementation Notes
1. Remove shims and adapt startup code (Task 3.1)
2. Replace remote EJB boundary (Task 3.2)
3. Upgrade audit library and adapt call sites (Task 3.3); generate audit tests (Task 3.5)

### Dependencies
- User Story 2

### Test Cases
- Test 1: Startup completes without WebLogic lifecycle classes
- Test 2: Shipping calculation works through the new in-process/REST boundary
- Test 3: v2.x audit logger emits expected records for checkout events

### Notes / Comments
- The repo's own ruleset makes the audit-library upgrade mandatory (Java 21 requirement already satisfied by Story 2)

---

## User Story 4: Messaging & Security Parity

**As a:** Site Reliability Engineer
**I want:** JMS order processing and Keycloak authentication working on Quarkus with multi-instance behavior
**So that:** Orders keep flowing and users keep authenticating exactly as on the EAP cluster, with no data loss or security regression

---

### Tasks
- ## 4. Messaging & Security Migration (from tasks.md)

### Acceptance Criteria
- [ ] `OrderServiceMDB` and `InventoryNotificationMDB` equivalents consume `topic/orders` on Quarkus with identical payload handling
- [ ] Two application instances process orders concurrently (HA parity demonstrated)
- [ ] OIDC login/logout works against the existing Keycloak `eap` realm; unauthenticated REST access is rejected
- [ ] SPA login → checkout journey passes E2E

### Details
- **Priority:** High
- **Estimate:** 8 story points

### Preconditions
- User Stories 2–3 complete

### Steps to Reproduce / Implementation Notes
1. Stand up Artemis consumers (Task 4.1) and verify dual-instance processing (Task 4.2)
2. Configure `quarkus-oidc` with the existing realm (Task 4.3) and adapt session handling (Task 4.4)
3. Generate messaging/security tests via mig-test-gen (Task 4.5)

### Dependencies
- User Stories 1–3

### Test Cases
- Test 1: Submitting an order produces the captured JMS processing output on both instances
- Test 2: Valid Keycloak user completes login and checkout; invalid token gets 401
- Test 3: `logout` (top graph hotspot) clears the session as before

### Notes / Comments
- Keep a parallel Artemis/Keycloak environment for A/B verification before cutover

---

## User Story 5: OpenShift Deployment & Cutover

**As a:** Product Owner
**I want:** Coolstore containerized and running on OpenShift with CI/CD, health checks, and externalized configuration
**So that:** The application is delivered on a supported platform with automated deployments and a safe rollback path

---

### Tasks
- ## 5. Frontend & Static Assets
- ## 6. Containerization & OpenShift Deployment
- ## 7. Cutover, Validation & Training (all from tasks.md)

### Acceptance Criteria
- [ ] Multi-stage Containerfile builds a scanned image pushed to the OpenShift registry
- [ ] Deployment (2 replicas) with Route, health probes, resource limits, HPA; ConfigMaps/Secrets for all external config
- [ ] PostgreSQL, Keycloak, and Artemis bound on-platform; `eap` realm imported
- [ ] SPA served from Quarkus static resources; bower components served from pinned CDN/npm
- [ ] Full E2E journey green on-cluster; performance meets or beats EAP baseline
- [ ] Cutover executed with rollback runbook; EAP decommissioned after stable window; team trained

### Details
- **Priority:** High
- **Estimate:** 13 story points

### Preconditions
- User Stories 1–4 complete

### Steps to Reproduce / Implementation Notes
1. Static-asset migration and bower replacement (Tasks 5.1–5.2)
2. Containerize and deploy via mig-containerize/mig-deploy (Tasks 6.1–6.8)
3. Smoke tests on-cluster (Task 6.9), performance validation, cutover, training (Tasks 7.1–7.6)

### Dependencies
- User Stories 1–4

### Test Cases
- Test 1: `/q/health` ready/live probes pass on both replicas
- Test 2: Login → browse → add-to-cart → checkout E2E passes on the OpenShift Route
- Test 3: Rolling update completes with zero dropped orders
- Test 4: Load test meets baseline latency/throughput

### Notes / Comments
- Keep the EAP environment warm for one sprint post-cutover for rollback; the pom's `<!-- TODO: Add OpenShift profile here -->` placeholder is resolved here (Task 6.6)
