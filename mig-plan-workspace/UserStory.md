# User Stories: CoolStore Monolith → Quarkus 3.x on OpenShift

> **Application:** coolstore-monolith (Java EE 7 WAR)  
> **Target:** Quarkus 3.x · Java 17 · Red Hat OpenShift 4.x  
> **Source:** mig-plan-workspace/tasks.md | mig-prompt-workspace/migration-prompt.md

---

## User Story 1: Secure, Scaffolded Quarkus Project Ready for Migration

**As a:** Platform Engineer  
**I want:** A new Quarkus 3.x project scaffold with secrets rotated, CI/CD pipeline wired, and characterisation tests capturing the monolith's REST contract  
**So that:** The team has a safe, reproducible starting point for all migration work without exposing the 3 embedded secrets identified by the security scan

---

### Tasks
- ## Task Group 1: Foundation & Project Scaffolding

### Acceptance Criteria
- [ ] All 3 embedded secrets are removed from source control and replaced by OpenShift Secrets; a `git log --all -S <secret>` scan returns no matches
- [ ] Quarkus 3.x Maven project builds successfully (`mvn package -DskipTests`) with all required extensions present
- [ ] Keycloak realm export (`realm-export.json`) maps correctly to `quarkus.oidc.*` properties; existing users and roles are preserved
- [ ] `javax.*` → `jakarta.*` namespace migration applied across all Java sources; no `javax.` import remains in application code
- [ ] OpenShift Tekton pipeline executes all stages (build → test → scan → deploy) on a PR trigger
- [ ] Characterisation test suite (12 endpoint contracts) passes green against the running monolith and is committed as a golden baseline

---

### Details
- **Priority:** High — blocker for all subsequent stories
- **Estimate:** 8 story points

---

### Preconditions
- Access to OpenShift cluster (`coolstore-dev` namespace provisioned)
- Existing monolith WAR deployable on JBoss EAP for characterisation test capture
- Quay.io repository configured for image pushes

---

### Steps to Reproduce / Implementation Notes
1. Run secret rotation (Task 1.1); verify with `git secrets` scan
2. Scaffold Quarkus project (Task 1.2–1.4)
3. Run OpenRewrite `JavaxMigrationToJakarta` recipe (Task 1.5)
4. Configure and validate Tekton pipeline (Task 1.6–1.7)
5. Capture characterisation tests against live monolith (Task 1.8)

---

### Dependencies
- None — first story in the sequence

---

### Test Cases
- Test 1: Secret scan — `git log --all -S 'weblogic.jndi.WLInitialContextFactory'` returns no commits after rotation
- Test 2: Quarkus build — `mvn package -DskipTests` exits 0 in under 2 minutes
- Test 3: Namespace migration — `grep -r 'javax\.' src/main/java` returns 0 results
- Test 4: Pipeline — PR to `main` triggers pipeline; all stages pass green

---

### Notes / Comments
- WebLogic stub classes (`weblogic/application/`) may still contain `javax.*` — they will be deleted in Task Group 4; namespace migration tool should skip them or they can be excluded and deleted manually first
- Characterisation tests are the safety net for the entire migration; invest time in coverage breadth

---

## User Story 2: Domain Model and Database Layer Running on Quarkus

**As a:** Backend Developer  
**I want:** All JPA entities and the persistence layer migrated to Quarkus Hibernate ORM with Panache, backed by a PostgreSQL datasource, with Flyway schema migration running automatically on startup  
**So that:** The application's data model is fully decoupled from the JBoss EAP JNDI datasource and can be deployed on OpenShift with a standard PostgreSQL database

---

### Tasks
- ## Task Group 2: Model Layer Migration (`com.redhat.coolstore.model`)
- ## Task Group 3: Persistence Layer Migration (`com.redhat.coolstore.persistence`)

### Acceptance Criteria
- [ ] All 8 model/entity classes compile with `jakarta.persistence.*` imports; zero `javax.persistence.*` references remain
- [ ] `CatalogItemEntity` and `InventoryEntity` use Panache entity annotations and pass round-trip persistence tests against PostgreSQL
- [ ] `Order` and `OrderItem` JPA `@OneToMany`/`@ManyToOne` relationships are correctly mapped and validated
- [ ] Flyway runs automatically on Quarkus startup (`quarkus.flyway.migrate-at-start=true`); all existing SQL migration scripts apply cleanly to PostgreSQL
- [ ] `DataBaseMigrationStartup.java` and `Resources.java` are deleted from the codebase
- [ ] Quarkus `@QuarkusTest` persistence integration tests pass with Testcontainers PostgreSQL
- [ ] ≥80% unit test line coverage on all model and utility transformer classes

---

### Details
- **Priority:** High — required by all service-layer stories
- **Estimate:** 5 story points

---

### Preconditions
- User Story 1 complete (Quarkus scaffold, namespace migration)
- PostgreSQL Testcontainers available in test classpath

---

### Steps to Reproduce / Implementation Notes
1. Copy and update model classes (Task 2.1–2.6)
2. Validate Flyway scripts against local PostgreSQL (Task 2.7)
3. Configure `application.properties` datasource (Task 3.2)
4. Remove `DataBaseMigrationStartup`, `Resources`, `Producers` (Task 3.3–3.6)
5. Run characterisation tests against model layer round-trips (Task 2.8, 3.7)

---

### Dependencies
- User Story 1 must be complete

---

### Test Cases
- Test 1: Flyway baseline — fresh PostgreSQL container; Quarkus starts; schema version table present; all scripts applied
- Test 2: `CatalogItemEntity` CRUD — persist, find by ID, update inventory quantity, verify via `em.find()`
- Test 3: `Transformers.toProduct()` — given `CatalogItemEntity` with inventory, assert all `Product` fields populated correctly
- Test 4: `Transformers.jsonToOrder()` → `Transformers.shoppingCartToJson()` round-trip — JSON in equals JSON out

---

### Notes / Comments
- Flyway 4.1.2 → 9.x: `flyway.baseline()` API changed; use `quarkus.flyway.baseline-on-migrate=true` property instead
- If Flyway checksum errors occur on existing scripts, run `flyway repair` once in dev then commit the updated checksum table

---

## User Story 3: Proprietary Audit Library Replaced and WebLogic Hooks Removed

**As a:** Security Engineer  
**I want:** The proprietary `audit-logging-library-1.0.0.jar` system-scoped dependency replaced with an open-source `AuditService`, and all WebLogic-specific lifecycle stubs removed from the codebase  
**So that:** The application has no undisclosed third-party binary dependencies, audit logs are observable via OpenShift logging infrastructure, and there are zero vendor-specific constructs remaining in utility code

---

### Tasks
- ## Task Group 4: Utility & Lifecycle Migration (`com.redhat.coolstore.utils`)

### Acceptance Criteria
- [ ] `weblogic/application/ApplicationLifecycleEvent.java` and `ApplicationLifecycleListener.java` are deleted from the codebase
- [ ] `StartupListener.java` observes Quarkus `StartupEvent`/`ShutdownEvent` with no WebLogic imports
- [ ] `AuditService.java` writes audit records to `./device-inventory-audit-logs/` in the same format as the original `FileSystemAuditLogger` (verified by comparing log output between old and new)
- [ ] `com.enterprise:audit-logging-library:1.0.0` `system`-scoped Maven dependency is removed from `pom.xml`
- [ ] `Transformers.java` compiles with `jakarta.json.*`; all characterisation tests for JSON serialisation pass
- [ ] `AuditService` unit tests pass with a temporary directory; `@PostConstruct`/`@PreDestroy` lifecycle is removed from `OrderService`
- [ ] `mvn dependency:analyze` reports no undeclared or unused dependencies in the `system` scope

---

### Details
- **Priority:** High — required before `OrderService` migration can complete
- **Estimate:** 5 story points

---

### Preconditions
- User Story 1 complete (namespace migration)
- Access to `lib/audit-logging-library-1.0.0.jar` for reverse-engineering log format (use `javap` or Fernflower decompiler)

---

### Steps to Reproduce / Implementation Notes
1. Decompile JAR to understand `AuditConfiguration` and `FileSystemAuditLogger` API and log format (Task 4.3)
2. Write `AuditService.java` CDI bean replicating behaviour (Task 4.3)
3. Delete WebLogic stubs (Task 4.1); rewrite `StartupListener` (Task 4.2)
4. Update `OrderService` to inject `AuditService` (Task 4.4)
5. Update `Transformers` imports (Task 4.5)
6. Remove system dependency from `pom.xml` (Task 4.6)

---

### Dependencies
- User Story 1 (namespace migration complete)
- User Story 2 (model layer complete, so `Transformers` changes can be tested end-to-end)

---

### Test Cases
- Test 1: `AuditService.logEvent()` — writes record to temp dir; file exists; content matches expected format
- Test 2: `StartupListener` — `@QuarkusTest` verifies `onStart()` logs startup message via Quarkus logging
- Test 3: `Transformers.shoppingCartToJson()` with Jakarta JSON — output is valid JSON; `orderValue`, `items` fields present
- Test 4: `mvn package` — no `system`-scoped dependency warnings; build succeeds without `audit-logging-library.jar`

---

### Notes / Comments
- If the decompiled JAR format cannot be reproduced exactly, obtain written sign-off from Security team accepting the new log format before removing the original JAR
- `AuditService` should also emit a structured log line (JSON) so OpenShift log aggregation captures audit events without reading the filesystem

---

## User Story 4: Business Services Running as Quarkus CDI Beans

**As a:** Backend Developer  
**I want:** All EJB service classes migrated to CDI beans on Quarkus with transaction management handled by `@Transactional`, the `@Stateful` shopping cart using CDI `@SessionScoped`, and the remote EJB shipping lookup replaced with a local CDI injection  
**So that:** The application's business logic runs entirely within Quarkus with no EJB container dependency, vendor-specific JNDI lookups, or remote EJB protocols

---

### Tasks
- ## Task Group 5: Service Layer Migration (`com.redhat.coolstore.service`)

### Acceptance Criteria
- [ ] All 7 service classes (`CatalogService`, `OrderService`, `ProductService`, `PromoService`, `ShippingService`, `ShoppingCartService`, `ShoppingCartOrderProcessor`) compile with zero EJB annotations (`@Stateless`, `@Stateful`, `@Remote`, `@MessageDriven`, `@ActivationConfigProperty`)
- [ ] `ShoppingCartService` annotated `@SessionScoped`; session state persists across requests within the same HTTP session
- [ ] `ShippingService` is a local `@ApplicationScoped` CDI bean; `ShippingServiceRemote.java` interface is deleted; no JNDI lookup code remains
- [ ] `ShoppingCartOrderProcessor.process()` publishes order message via SmallRye `Emitter` (JMS `@Inject JMSContext` removed)
- [ ] `CatalogService.updateInventoryItems()` and `OrderService.save()` are `@Transactional`; rollback on exception verified
- [ ] All service unit tests (PromoService, ShippingService pricing bands) pass; `@QuarkusTest` integration tests for `CatalogService` and `OrderService` pass with Testcontainers PostgreSQL
- [ ] `ShippingServiceRemote.java` is deleted from the codebase

---

### Details
- **Priority:** High — required before REST and messaging stories
- **Estimate:** 8 story points

---

### Preconditions
- User Stories 1, 2, and 3 complete
- AMQ Artemis Testcontainers available for `ShoppingCartOrderProcessor` integration test

---

### Steps to Reproduce / Implementation Notes
1. Migrate `CatalogService` and `OrderService` (Tasks 5.1–5.2); run `@QuarkusTest` after each
2. Migrate `ProductService` and `PromoService` (Tasks 5.3–5.4)
3. Migrate `ShippingService` and delete `ShippingServiceRemote` (Tasks 5.5, 5.8)
4. Migrate `ShoppingCartService` — most complex step (Task 5.6); test session state manually
5. Migrate `ShoppingCartOrderProcessor` to `Emitter` (Task 5.7)

---

### Dependencies
- User Stories 1, 2, 3 must be complete

---

### Test Cases
- Test 1: `PromoService` — item `329299` in cart; `applyCartItemPromotions()` sets price to 75% of original; savings = -25%
- Test 2: `PromoService` — cart total ≥ $75; `applyShippingPromotions()` sets shipping to $0
- Test 3: `ShippingService.calculateShipping()` — cart total $30 → $4.99; $60 → $6.99; $150 → $10.99
- Test 4: `CatalogService.updateInventoryItems()` — deduct quantity; verify persisted in PostgreSQL; rollback on exception
- Test 5: `ShoppingCartService` session scope — two requests with same session cookie return same cart state
- Test 6: `ShoppingCartOrderProcessor` — `process(cart)` sends AMQP message to `topic/orders`; Testcontainers consumer receives it

---

### Notes / Comments
- If Quarkus HTTP session scope is unavailable without `quarkus-undertow`, evaluate using a stateless cart model backed by a Redis `@SessionScoped` cache as an alternative; document the decision in an ADR
- `@Transactional` on `checkOutShoppingCart()` ensures the message send and cart reset are atomic (or compensating)

---

## User Story 5: Asynchronous Messaging Migrated to AMQ Artemis

**As a:** Backend Developer  
**I want:** The two JMS Message-Driven Beans (`OrderServiceMDB` and `InventoryNotificationMDB`) replaced with SmallRye Reactive Messaging consumers connected to an AMQ Artemis broker on OpenShift, with the WebLogic T3 JNDI connection in `InventoryNotificationMDB` completely removed  
**So that:** The application's asynchronous order processing and inventory notification flows work reliably on OpenShift with no WebLogic or WildFly-specific messaging infrastructure

---

### Tasks
- ## Task Group 6: Messaging Migration — MDB Replacement

### Acceptance Criteria
- [ ] `OrderServiceMDB.java` has no EJB annotations; `@Incoming("orders")` method receives AMQP messages from `topic/orders` and correctly persists orders and updates inventory
- [ ] `InventoryNotificationMDB.java` has no WebLogic T3 JNDI code (`WLInitialContextFactory`, `PortableRemoteObject`, `t3://localhost:7001`); `@Incoming("orders-inventory")` consumer logs low-stock alerts when quantity < 50
- [ ] AMQ Broker CR deployed on OpenShift with `topic/orders` address (multicast routing); AMQP credentials stored in Secret
- [ ] Dead-letter address configured; failed messages visible in broker management console
- [ ] `@QuarkusTest` messaging integration tests pass with Testcontainers ActiveMQ Artemis (end-to-end: publish → persist → inventory alert)
- [ ] No JMS 2.0 imports (`javax.jms.*` / `jakarta.jms.*`) remain in `OrderServiceMDB` or `InventoryNotificationMDB`

---

### Details
- **Priority:** High — inventory management and order persistence depend on this
- **Estimate:** 8 story points

---

### Preconditions
- User Stories 1, 2, 4 complete
- AMQ Broker Operator installed on OpenShift cluster
- Task 5.7 (`ShoppingCartOrderProcessor` Emitter) complete

---

### Steps to Reproduce / Implementation Notes
1. Deploy AMQ Broker on OpenShift (Task 6.1)
2. Configure AMQP connector in `application.properties` (Task 6.2)
3. Rewrite `OrderServiceMDB` as reactive consumer (Task 6.3)
4. Rewrite `InventoryNotificationMDB` as reactive consumer, delete all WebLogic T3 code (Task 6.4)
5. Configure channel mappings (Task 6.5); validate delivery semantics (Task 6.6)
6. Run end-to-end messaging integration test (Task 6.7)

---

### Dependencies
- User Stories 1, 2, 4 must be complete

---

### Test Cases
- Test 1: Publish order JSON to `topic/orders` via Testcontainers Artemis; verify `Order` row persisted in PostgreSQL
- Test 2: Publish order with item quantity 10 (below threshold 50); verify low-stock log message emitted
- Test 3: Malformed order message → routed to dead-letter address; application does not crash; error logged
- Test 4: AMQ Broker pod restart; consumer reconnects within 30 seconds; no message loss

---

### Notes / Comments
- `InventoryNotificationMDB` originally connected to WebLogic T3 as a *separate* subscriber from `OrderServiceMDB` — ensure both consumers in Quarkus use separate AMQP consumer groups (different `mp.messaging.incoming.*.group-id` values) to both receive every order message independently

---

## User Story 6: REST API Layer Migrated to Quarkus RESTEasy Reactive with OIDC Security

**As a:** API Consumer  
**I want:** All three REST endpoint classes (`CartEndpoint`, `ProductEndpoint`, `OrderEndpoint`) running on Quarkus RESTEasy Reactive with Keycloak OIDC authentication enforcing the same role-based access control as the monolith  
**So that:** Clients (AngularJS SPA, integration partners) experience identical API behaviour with improved performance and security, and the taint vulnerability identified by rgctl is remediated

---

### Tasks
- ## Task Group 7: REST Layer Migration (`com.redhat.coolstore.rest`)

### Acceptance Criteria
- [ ] All 12 REST operations (`GET /products/`, `GET /products/{itemId}`, `GET /orders/`, `GET /orders/{orderId}`, `GET /cart/{cartId}`, `POST /cart/checkout/{cartId}`, `POST /cart/{cartId}/{itemId}/{quantity}`, `POST /cart/{cartId}/{tmpId}`, `DELETE /cart/{cartId}/{itemId}/{quantity}`) return responses identical to characterisation test golden baseline
- [ ] No `javax.ws.rs.*` or `javax.enterprise.context.*` imports remain; all use `jakarta.*` equivalents
- [ ] Keycloak OIDC roles mapped to endpoints via `@RolesAllowed`; unauthenticated requests return 401
- [ ] The 1 taint flow identified by rgctl is remediated; `@Valid` / Bean Validation applied to user-supplied path and body parameters
- [ ] OpenAPI specification (`/q/openapi`) auto-generated and committed to `docs/openapi.yaml`
- [ ] All `@QuarkusTest` REST tests pass, including OIDC-gated tests using `@TestSecurity`

---

### Details
- **Priority:** High — this is the externally visible API layer; highest harmonic centrality (rgctl rank 13)
- **Estimate:** 8 story points

---

### Preconditions
- User Stories 1–5 complete
- Keycloak/RHSSO deployed on OpenShift (Task 9.4)

---

### Steps to Reproduce / Implementation Notes
1. Migrate `RestApplication`, `ProductEndpoint`, `OrderEndpoint` (Tasks 7.1–7.3) — low risk; validate immediately with characterisation tests
2. Migrate `CartEndpoint` (Task 7.4) — highest complexity; test each operation individually
3. Wire OIDC security (Task 7.5); test with `@TestSecurity(user="testuser", roles="user")`
4. Remediate taint flow (Task 7.6); add `@Valid` to affected endpoints
5. Generate and commit OpenAPI spec (Task 7.10)

---

### Dependencies
- All previous user stories (1–5) must be complete
- Keycloak deployment on OpenShift (from User Story 8)

---

### Test Cases
- Test 1: `GET /products/` — returns same product list JSON as monolith characterisation test; status 200
- Test 2: `POST /cart/{cartId}/{itemId}/{quantity}` — adds item; returns updated cart with correct total and promo savings applied
- Test 3: `POST /cart/checkout/{cartId}` — returns cart; order message published to AMQP topic; cart reset
- Test 4: `DELETE /cart/{cartId}/{itemId}/2` — removes 2 of item; partial removal leaves remainder in cart
- Test 5: Unauthenticated `GET /orders/` — returns 401 Unauthorized
- Test 6: Taint flow — `POST /cart/{cartId}/{itemId}/{quantity}` with negative quantity — `@Min(1)` validation returns 400 Bad Request

---

### Notes / Comments
- `CartEndpoint` is `@SessionScoped` — RESTEasy Reactive supports this via `quarkus-undertow`; test session affinity in multi-pod deployment (may require sticky sessions on OpenShift Route or stateless cart refactor)
- Prioritise characterisation test parity over refactoring elegance in this story; refactoring is a future workstream

---

## User Story 7: Application Containerised and Deployed to OpenShift

**As a:** Platform Engineer  
**I want:** The migrated Quarkus application packaged as a container image, all infrastructure (PostgreSQL, AMQ Broker, Keycloak) deployed on OpenShift, and a full GitOps pipeline automating build-to-deploy  
**So that:** The application runs reliably on OpenShift with automated deployments, health checks, autoscaling, and secure secret management, ready for production traffic

---

### Tasks
- ## Task Group 8: Containerisation
- ## Task Group 9: OpenShift Deployment & Infrastructure

### Acceptance Criteria
- [ ] `Containerfile.jvm` builds a runnable UBI9 OpenJDK 17 image; Quarkus starts in < 5 seconds inside the container
- [ ] Image scan (ACS / Trivy) reports 0 critical or high CVEs
- [ ] All infrastructure components (PostgreSQL, AMQ Broker, Keycloak) are running on OpenShift with Operator-managed lifecycle
- [ ] All 6 Kubernetes manifests (`Deployment`, `Service`, `Route`, `ConfigMap`, `Secret`, `HPA`) applied successfully to `coolstore-dev` namespace
- [ ] Liveness probe (`/q/health/live`) and readiness probe (`/q/health/ready`) both return 200 within 30 seconds of pod start
- [ ] ArgoCD `Application` CR syncs automatically on `main` branch push; `oc rollout status` exits 0
- [ ] `HorizontalPodAutoscaler` scales from 2 → 4 replicas under simulated load (k6 ramp test)
- [ ] Audit log `PersistentVolumeClaim` mounted; `AuditService` writes to PVC path; files persist across pod restart

---

### Details
- **Priority:** High — required for production readiness
- **Estimate:** 8 story points

---

### Preconditions
- User Stories 1–6 complete
- OpenShift cluster with AMQ Broker Operator, Keycloak Operator, CrunchyData PGO Operator installed
- Quay.io repository and push credentials configured

---

### Steps to Reproduce / Implementation Notes
1. Write `Containerfile.jvm` and test locally with Podman (Task 8.1)
2. Add image build + scan to Tekton pipeline (Tasks 8.3–8.5)
3. Deploy infrastructure components in order: PostgreSQL → AMQ Broker → Keycloak (Tasks 9.2–9.4)
4. Create Secrets and ConfigMaps (Tasks 9.5–9.6); apply Deployment, Service, Route, HPA manifests (Tasks 9.7–9.10)
5. Configure ArgoCD GitOps (Task 9.12); trigger a test deployment from a PR merge

---

### Dependencies
- User Stories 1–6 must be complete
- Cluster admin access to install operators

---

### Test Cases
- Test 1: `podman run --rm coolstore-quarkus:latest` — `/q/health/ready` returns `{"status":"UP"}` within 5 seconds
- Test 2: Image scan — `roxctl image check` or `trivy image` returns 0 critical/high CVEs
- Test 3: `oc apply -f deploy/` — all resources created; `oc get pods` shows Running state within 60 seconds
- Test 4: Liveness / readiness probes — `curl http://coolstore.apps.<cluster>/q/health/live` returns 200
- Test 5: HPA scale-out — k6 ramp to 200 VUs; `oc get hpa` shows replicas increasing; p95 latency stays within baseline
- Test 6: PVC persistence — write audit log entry; delete pod; new pod starts; audit log file still present

---

### Notes / Comments
- If `@SessionScoped` requires sticky sessions, configure OpenShift Route `haproxy.router.openshift.io/balance: source` annotation
- For native image (optional): use `Containerfile.native` with multi-stage GraalVM build; validate startup < 1 second

---

## User Story 8: Production Cutover with Zero Downtime

**As a:** Product Owner  
**I want:** A validated parallel-run period followed by a canary traffic cutover from the monolith to the Quarkus application, with a clear rollback procedure available throughout  
**So that:** Users experience no downtime or functional regression during the transition, and the team can safely decommission the legacy JBoss EAP infrastructure after a 30-day stability window

---

### Tasks
- ## Task Group 10: Parallel Run, Validation & Cutover

### Acceptance Criteria
- [ ] All characterisation tests (12 REST operations) pass green against Quarkus in `coolstore-staging`
- [ ] Quarkus p95 latency ≤ monolith baseline under identical Gatling / k6 load profile
- [ ] OWASP ZAP scan on Quarkus endpoints: 0 critical findings; taint flow from Task 7.6 confirmed remediated
- [ ] 48-hour parallel run in staging completes with no divergence between monolith and Quarkus responses
- [ ] Canary cutover: 10% → 50% → 100% traffic routed to Quarkus over 72 hours with no increase in error rate or p95 latency degradation > 10%
- [ ] Stakeholder sign-off obtained and documented
- [ ] Monolith WAR deployment decommissioned after 30-day stability window
- [ ] Post-migration report delivered with performance comparison, test results, and lessons learned

---

### Details
- **Priority:** High — final delivery gate
- **Estimate:** 5 story points

---

### Preconditions
- User Story 7 complete (Quarkus deployed on OpenShift)
- OpenShift Service Mesh configured for weight-based traffic splitting (or OpenShift Route weight annotations)
- Monolith WAR still running on JBoss EAP in parallel

---

### Steps to Reproduce / Implementation Notes
1. Deploy Quarkus in `coolstore-dev`; run full characterisation + performance test suite (Tasks 10.2–10.3)
2. Run OWASP ZAP scan (Task 10.4); resolve any findings before proceeding
3. Promote to `coolstore-staging`; run 48-hour parallel run (Task 10.5)
4. Obtain sign-off (Task 10.6)
5. Canary cutover in `coolstore-prod`: 10% → 50% → 100% over 72 hours (Task 10.7)
6. After 30-day window: decommission monolith, remove audit JAR (Tasks 10.8–10.9)
7. Deliver post-migration report (Task 10.10)

---

### Dependencies
- All previous user stories (1–7) must be complete and signed off

---

### Test Cases
- Test 1: Characterisation suite — all 12 endpoint contract tests pass against Quarkus in staging
- Test 2: Performance — k6 load: 100 VUs, 10 min; Quarkus p95 ≤ monolith p95; error rate < 0.1%
- Test 3: ZAP scan — `zap-baseline.py -t http://coolstore.apps.<cluster>/api` — 0 FAIL alerts
- Test 4: Canary at 10% — monitor 24h; `oc adm top pod` shows Quarkus pods handling ~10% of requests; error rate unchanged
- Test 5: Rollback drill — revert ArgoCD to previous image tag; `oc rollout status` exits 0 within 2 minutes; 100% traffic back to monolith

---

### Notes / Comments
- Keep monolith running for the full 30-day stability window even after 100% cutover — this is the rollback safety net
- Schedule decommission during a low-traffic window; notify all stakeholders in advance
- Archive monolith WAR and configuration in a `legacy/` Git tag before removal

---

## Story Dependency Map

```
Story 1 (Foundation)
    └── Story 2 (Model & Persistence)
            └── Story 3 (Utilities & Audit)
                    └── Story 4 (Services)
                            ├── Story 5 (Messaging)
                            └── Story 6 (REST API)
                                    └── Story 7 (Containerise & Deploy)
                                            └── Story 8 (Cutover)
```

## Estimation Summary

| Story | Description | Points | Priority |
|-------|-------------|--------|----------|
| 1 | Foundation & Scaffolding | 8 | High |
| 2 | Model & Persistence | 5 | High |
| 3 | Utilities & Audit Replacement | 5 | High |
| 4 | Service Layer (EJB → CDI) | 8 | High |
| 5 | Messaging (MDB → Reactive) | 8 | High |
| 6 | REST Layer & OIDC | 8 | High |
| 7 | Containerisation & Deployment | 8 | High |
| 8 | Parallel Run & Cutover | 5 | High |
| **Total** | | **55 points** | |

> **Estimated duration:** 8–10 sprints (2-week sprints) with a team of 2–3 developers, assuming 1 sprint = ~6 usable story points.
