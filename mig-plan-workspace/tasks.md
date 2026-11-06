# Migration Tasks

CoolStore Monolith → Quarkus 3 / Java 21 / OpenShift

Source facts: rgctl knowledge graph (`.rgctl/migration_plan.json`, hybrid preset — package order `model → utils → service → rest → persistence`), migration prompt at `mig-prompt-workspace/migration-prompt.md`, mandatory ruleset `rules/rule.yaml` (audit-logging 1.x → 2.0.0, openjdk8 → quarkus3).

Task groups are dependency-ordered: Foundation (TG1–2) → Data/Model (TG3–4) → Core packages per rgctl order (TG5 utils → TG6 service → TG7 JMS → TG8 audit → TG9 rest) → Security/Frontend (TG10–11) → Container/Deploy (TG12–13) → Cutover (TG14).

## Task Group 1: Quarkus 3 Foundation & Build Pipeline

- [ ] 1.1 Convert Maven build from WAR to Quarkus JAR packaging: add `io.quarkus.platform:quarkus-bom` (3.x LTS) to `dependencyManagement`, add `quarkus-maven-plugin`, set Java release to 21, remove `maven-war-plugin` and `<finalName>ROOT</finalName>`
- [ ] 1.2 Remove Java EE provided deps (`javaee-web-api`, `javaee-api`, `jboss-jms-api_2.0_spec`, `jboss-rmi-api_1.0_spec`) and add Quarkus extensions: `quarkus-rest-jackson` (JAX-RS), `quarkus-hibernate-orm` (JPA), `quarkus-flyway`, `quarkus-oidc`, messaging extension (see TG7)
- [ ] 1.3 Replace system-scoped `com.enterprise:audit-logging-library:1.0.0` (`systemPath` to `lib/audit-logging-library-1.0.0.jar`) with `lib/audit-logging-library-2.0.0.jar` installed to a local/internal Maven repository, version 2.0.0 (mandatory rule `audit-logging-0001`)
- [ ] 1.4 Remove `<maven.test.skip>true</maven.test.skip>`; configure `maven-compiler-plugin` release 21; verify whole build compiles (expect `javax.*` errors → resolved incrementally in TG3–TG9)
- [ ] 1.5 Add the `openshift` Maven profile pre-marked by `TODO: Add OpenShift profile here` in `pom.xml` (base image, registry, ports) — completed in TG13
- [ ] 1.6 Set up CI pipeline (OpenShift Pipelines/Tekton or GitHub Actions): build → unit tests → image build → scan, on every PR
- [ ] 1.7 **Test Generation**: Use mig-test-gen to create the build/CI smoke test
  - Command: `/skill mig-test-gen` for the Quarkus build skeleton
  - Expected: a CI job that fails if `mvn package` or Java 21 compile fails
- [ ] 1.8 **Containerization**: Use mig-containerize to draft the multi-stage builder image
  - Command: `/skill mig-containerize` for coolstore-quarkus (builder stage)
  - Config needs: base image `registry.access.redhat.com/ubi8/openjdk-21`, no secrets yet
- [ ] 1.9 **Deployment**: Use mig-deploy to register the target namespace/project layout
  - Command: `/skill mig-deploy` for coolstore (planning: namespace, app name, ports 8080)
  - Dependencies: OpenShift cluster access, internal image registry
  - Network: expose 8080 (http), later probes `/q/health`
- [ ] 1.10 **Documentation**: Document the build change
  - Update README with Java 21/Quarkus build prerequisites
  - Record the WAR→JAR and audit-lib 2.0.0 decisions in the migration decision log

## Task Group 2: Characterization Test Baseline (before code changes)

- [ ] 2.1 Stand up the current stack (JBoss EAP 7.4 + PostgreSQL + Keycloak per README) and record baseline behavior: REST contracts, startup time, memory footprint
- [ ] 2.2 Write characterization (contract) tests for REST endpoints: `GET /cart/{cartId}`, `POST /cart/{cartId}/{itemId}/{quantity}`, `POST /cart/checkout/{cartId}`, `DELETE /cart/{cartId}/{itemId}/{quantity}`, `GET /products`, `GET /products/{itemId}`, `GET /orders`, `GET /orders/{orderId}` — capture status codes and JSON shapes (runnable against both old and new app)
- [ ] 2.3 Capture the JMS message contract: `Transformers.shoppingCartToJson(cart)` payload consumed by `OrderServiceMDB` (`jsonToOrder`) on `topic/orders`
- [ ] 2.4 Capture checkout side effects: order persisted, `CatalogService.updateInventoryItems` decrements inventory, audit log file `./device-inventory-audit-logs` written by `OrderService`
- [ ] 2.5 **Test Generation**: Use mig-test-gen to formalize the characterization suite
  - Command: `/skill mig-test-gen` targeting REST + JMS contracts
  - Expected: runnable contract test suite (HTTP + message assertions) checked into the repo
- [ ] 2.6 **Containerization**: N/A for this group (tests only) — reuse TG1 builder image for test execution
- [ ] 2.7 **Deployment**: N/A — tests run in CI (TG1.6)
- [ ] 2.8 **Documentation**: Document the recorded baseline (performance numbers, contracts) in `mig-plan-workspace/` notes referenced by TG14

## Task Group 3: Data Foundation — PostgreSQL, Hibernate ORM, Flyway, Persistence Producer

- [ ] 3.1 Configure `application.properties`: `quarkus.datasource.db-kind=postgresql`, JDBC URL/user/password via env vars (`QUARKUS_DATASOURCE_*`), replacing JBoss `CoolstoreDS` JTA datasource and `module.xml` driver install
- [ ] 3.2 Simplify `src/main/resources/META-INF/persistence.xml` (PU `primary`): drop `jta-data-source java:jboss/datasources/CoolstoreDS` and Hibernate vendor props (`hibernate.jdbc.use_get_generated_keys=false` etc.), let Quarkus manage the PU
- [ ] 3.3 Enable `quarkus-flyway` `migrate-at-start=true` using existing `db/migration/V1_1__CreateSchema.sql`, `V1_2__AddInitialData.sql`; retire `utils/DataBaseMigrationStartup` `@Singleton` bootstrap
- [ ] 3.4 Replace `persistence/Resources.java` (`@PersistenceContext` + `@Produces EntityManager`) with direct `@Inject EntityManager` (Quarkus-managed) in consumers — remove the CDI producer class
- [ ] 3.5 Verify schema/data continuity against the existing PostgreSQL database (Flyway history preserved, no re-generation: `javax.persistence.schema-generation.database.action=none` semantics kept)
- [ ] 3.6 **Test Generation**: Use mig-test-gen for the persistence layer
  - Command: `/skill mig-test-gen` targeting datasource/Flyway wiring
  - Expected: `@QuarkusTest` with Testcontainers PostgreSQL asserting Flyway ran and PU boots
- [ ] 3.7 **Containerization**: Use mig-containerize to parameterize DB env vars in the image recipe
  - Command: `/skill mig-containerize` for coolstore-quarkus
  - Config needs: `QUARKUS_DATASOURCE_USERNAME`/`PASSWORD` (Secret), JDBC URL (ConfigMap)
- [ ] 3.8 **Deployment**: Use mig-deploy to wire the database connection
  - Command: `/skill mig-deploy` for coolstore
  - Dependencies: PostgreSQL service (cluster-internal or managed)
  - Network: app → postgres:5432; credentials from OpenShift Secret
- [ ] 3.9 **Documentation**: Document datasource config, Flyway behavior, and removed JBoss datasource steps; update runbook

## Task Group 4: Model Layer Migration (`com.redhat.coolstore.model`)

- [ ] 4.1 Migrate all 8 model classes (`CatalogItemEntity`, `InventoryEntity`, `Order`, `OrderItem`, `Product`, `Promotion`, `ShoppingCart`, `ShoppingCartItem`) from `javax.persistence.*` to `jakarta.persistence.*`
- [ ] 4.2 Verify entity mappings against PostgreSQL schema from `V1_1__CreateSchema.sql` (tables, columns, `@GeneratedValue` strategy — note current `use_get_generated_keys=false` setting)
- [ ] 4.3 Keep `Serializable` and field/JSON shapes unchanged (frontend + JMS contract depend on them — see TG2.2/2.3)
- [ ] 4.4 **Test Generation**: Use mig-test-gen for entities
  - Command: `/skill mig-test-gen` targeting the model package
  - Expected: entity round-trip tests (persist/find via Testcontainers PostgreSQL; JSON serialization contract tests)
- [ ] 4.5 **Containerization**: covered by TG1.8 image (no model-specific container work)
- [ ] 4.6 **Deployment**: covered by TG3.8 (same deployment unit)
- [ ] 4.7 **Documentation**: Note namespace migration and any mapping fixes in the decision log

## Task Group 5: Utils Layer & WebLogic Legacy Cleanup (`com.redhat.coolstore.utils` + `weblogic.*`)

- [ ] 5.1 Migrate `Transformers.java` (Jackson helpers `shoppingCartToJson`, `jsonToOrder`) — keep signatures and JSON format byte-compatible (JMS + frontend contracts)
- [ ] 5.2 Migrate `Producers.java` (CDI `Logger` producer) — keep `@Inject Logger` working via `java.util.logging` or Quarkus logging
- [ ] 5.3 Delete `weblogic/application/ApplicationLifecycleListener.java`, `ApplicationLifecycleEvent.java`, `weblogic/i18n/logging/NonCatalogLogger.java` shim classes (not referenced by coolstore code; only `StartupListener` extends them)
- [ ] 5.4 Replace `StartupListener` (WebLogic `ApplicationLifecycleListener`) with a Quarkus `@Observes StartupEvent`/`ShutdownEvent` CDI bean logging "AppListener(postStart)"/"(preStop)" equivalents
- [ ] 5.5 **Test Generation**: Use mig-test-gen for utils
  - Command: `/skill mig-test-gen` targeting Transformers + startup events
  - Expected: JSON contract unit tests; startup log assertion in `@QuarkusTest`
- [ ] 5.6 **Containerization**: covered by TG1.8 image
- [ ] 5.7 **Deployment**: covered by TG3.8
- [ ] 5.8 **Documentation**: Record WebLogic shim removal and lifecycle replacement; note that `weblogic.*` imports must no longer compile

## Task Group 6: Service Layer Migration — EJB → CDI (`com.redhat.coolstore.service`)

- [ ] 6.1 Convert `@Stateless` beans to `@ApplicationScoped` CDI + `@Transactional` where they write via EntityManager: `CatalogService`, `OrderService`, `ProductService`, `ShippingService`, `ShoppingCartOrderProcessor`
- [ ] 6.2 Convert `@Stateful ShoppingCartService` (in-memory `ShoppingCart cart` field, per-user state) to a stateless design keyed by `cartId` (map-backed `@ApplicationScoped` store with expiry, or HTTP-session-backed bean) — decision recorded; multi-replica safety validated in TG13
- [ ] 6.3 Keep `PromoService` (`@ApplicationScoped`) and `ShippingService`/`ShippingServiceRemote` shipping math (`calculateShipping`, `calculateShippingInsurance`) behavior identical — drop `javax.ejb.Remote` on `ShippingService`, keep the interface as plain Java
- [ ] 6.4 Replace `@Inject EntityManager` paths (former `Resources` producer) and remove JNDI/`InitialContext` usage in `ShoppingCartService` (Hashtable/Context imports)
- [ ] 6.5 **Test Generation**: Use mig-test-gen for services
  - Command: `/skill mig-test-gen` targeting service package
  - Expected: unit tests per service (pricing/promo/shipping math ≥80%), cart-state tests incl. concurrent carts per user
- [ ] 6.6 **Containerization**: covered by TG1.8 image; note cart-state strategy implications for replicas in image env docs
- [ ] 6.7 **Deployment**: covered by TG3.8; flag cart-state design on Deployment env (TG13)
- [ ] 6.8 **Documentation**: Document EJB→CDI mapping table and the cart state strategy decision

## Task Group 7: JMS Order Flow Migration (`topic/orders`)

- [ ] 7.1 Replace `ShoppingCartOrderProcessor` JMS publish (`@Inject JMSContext`, `@Resource(lookup = "java:/topic/orders")`) with Quarkus messaging (`quarkus-smallrye-reactive-messaging` with Artemis, or `quarkus-artemis-jms` emitter) configured for `topic/orders`
- [ ] 7.2 Replace `OrderServiceMDB` `@MessageDriven`/`MessageListener` with a Quarkus `@Incoming("orders")` consumer preserving: TextMessage JSON body, auto-acknowledge, `jsonToOrder` parse, `orderService.save(order)`, `catalogService.updateInventoryItems(...)` loop
- [ ] 7.3 Delete `InventoryNotificationMDB` (WebLogic JMS client: `WLInitialContextFactory`, `t3://localhost:7001`, `PortableRemoteObject`) — dead legacy path; port its low-inventory threshold logic (`LOW_THRESHOLD=50`) as a log/warning inside the consumer if still wanted
- [ ] 7.4 Wire an AMQ Broker (Artemis) on OpenShift (or in-cluster equivalent) with `topic/orders`; document cluster/pub-sub semantics replacing the 2-node EAP `standalone-full-ha` test from README
- [ ] 7.5 **Test Generation**: Use mig-test-gen for messaging
  - Command: `/skill mig-test-gen` targeting the order flow
  - Expected: integration test — publish cart JSON → order persisted + inventory decremented (Testcontainers Artemis + PostgreSQL)
- [ ] 7.6 **Containerization**: Use mig-containerize to add broker env wiring
  - Command: `/skill mig-containerize` for coolstore-quarkus
  - Config needs: broker URL + credentials (Secret), topic name (ConfigMap)
- [ ] 7.7 **Deployment**: Use mig-deploy for messaging dependencies
  - Command: `/skill mig-deploy` for coolstore + AMQ broker
  - Dependencies: AMQ Broker instance with `topic/orders`
  - Network: app → broker ports (61616/5671)
- [ ] 7.8 **Documentation**: Document the message contract, consumer semantics, and removed WebLogic JMS path

## Task Group 8: Audit Logging Library v2 Upgrade (mandatory ruleset `rules/rule.yaml`)

- [ ] 8.1 Rule `audit-logging-0001`: depend on `audit-logging-library:2.0.0` (done in TG1.3 — verify no 1.x references remain in `pom.xml`)
- [ ] 8.2 Rule `audit-logging-0002`: replace `AuditEvent.builder()` pattern with direct Java 21 record instantiation `new AuditEvent(...)` (v2 JAR: `com.enterprise.audit.logging.model.AuditEvent` record)
- [ ] 8.3 Rule `audit-logging-0003`: replace `FileSystemAuditLogger` (file target `./device-inventory-audit-logs`) in `OrderService.init()` with `StreamableAuditLogger` configured for TCP streaming (endpoint from config/Secret, not hardcoded)
- [ ] 8.4 Rule `audit-logging-0004`: use non-blocking `logEventAsync(event)` instead of synchronous `logEvent(event)`
- [ ] 8.5 Rule `audit-logging-0005`: remove legacy `logSuccess`/`logFailure` convenience calls; construct full `AuditEvent` records and call `logEventAsync`
- [ ] 8.6 Rework `OrderService` `@PostConstruct init()`/`@PreDestroy cleanup()` lifecycle around `AuditConfiguration` + `StreamableAuditLogger`; emit audit events for order save (replacing the file writes characterized in TG2.4)
- [ ] 8.7 **Test Generation**: Use mig-test-gen for audit logging
  - Command: `/skill mig-test-gen` targeting OrderService audit paths
  - Expected: tests asserting an `AuditEvent` is emitted asynchronously on order save (TCP stub/mock listener)
- [ ] 8.8 **Containerization**: Use mig-containerize for audit endpoint config
  - Command: `/skill mig-containerize` for coolstore-quarkus
  - Config needs: audit TCP host/port (Secret/ConfigMap); no local `device-inventory-audit-logs` volume
- [ ] 8.9 **Deployment**: Use mig-deploy to wire the audit endpoint
  - Command: `/skill mig-deploy` for coolstore
  - Dependencies: audit log collector reachable over TCP (or in-cluster service)
  - Network: app → audit collector TCP port
- [ ] 8.10 **Documentation**: Document v2 API usage, TCP endpoint config, and rule-by-rule compliance evidence (all five `audit-logging-*` rules green)

## Task Group 9: REST Layer & Cart State (`com.redhat.coolstore.rest`)

- [ ] 9.1 Migrate `RestApplication` + endpoints (`CartEndpoint`, `OrderEndpoint`, `ProductEndpoint`) from `javax.ws.rs.*` to `jakarta.ws.rs.*` on `quarkus-rest`; preserve paths `/cart`, `/orders`, `/products` and JSON media types (contracts from TG2.2)
- [ ] 9.2 Replace `@SessionScoped CartEndpoint` with the TG6.2 cart-state strategy (cart identified by `cartId` path param — already in API design); ensure checkout (`/cart/checkout/{cartId}`) triggers `ShoppingCartOrderProcessor`
- [ ] 9.3 Keep `@RequestScoped OrderEndpoint`/`ProductEndpoint` semantics; add Quarkus health (`/q/health`, `/q/health/live`, `/q/health/ready`) replacing `health.jsp`
- [ ] 9.4 **Test Generation**: Use mig-test-gen for REST layer
  - Command: `/skill mig-test-gen` targeting REST endpoints
  - Expected: `@QuarkusTest` endpoint tests (all routes from TG2.2), auth-on/off variants, health probe tests
- [ ] 9.5 **Containerization**: covered by TG1.8 image; expose `/q/health` for probes (TG13)
- [ ] 9.6 **Deployment**: covered by TG13 (probes + routes)
- [ ] 9.7 **Documentation**: Update API docs with preserved contracts + new health endpoints

## Task Group 10: Security — Keycloak OIDC

- [ ] 10.1 Add `quarkus-oidc`: server-side token validation for realm `eap`, client `eap-app` (`realm-export.json`), `quarkus.oidc.auth-server-url`, tenant config via env vars
- [ ] 10.2 Secure checkout/order endpoints (Bearer token) while keeping catalog browsing public — matching current client-side adapter behavior in `keycloak.json`/`coolstore.json` (`SSO_ENABLED: true`)
- [ ] 10.3 Adapt the SPA sign-in flow: keep Keycloak JS adapter for login, tokens now accepted server-side; verify redirect URIs against the OpenShift Route
- [ ] 10.4 Move the 8 potential secrets flagged by rgctl out of source into OpenShift Secrets/ConfigMaps (DB creds, Keycloak client config, audit TCP endpoint)
- [ ] 10.5 **Test Generation**: Use mig-test-gen for security
  - Command: `/skill mig-test-gen` targeting OIDC-secured endpoints
  - Expected: tests — unauthenticated checkout → 401; valid token → 200; wrong realm/client rejected
- [ ] 10.6 **Containerization**: Use mig-containerize for OIDC env wiring
  - Command: `/skill mig-containerize` for coolstore-quarkus
  - Config needs: OIDC issuer URL (ConfigMap), client secret (Secret)
- [ ] 10.7 **Deployment**: Use mig-deploy for Keycloak wiring
  - Command: `/skill mig-deploy` for coolstore + keycloak
  - Dependencies: Keycloak instance with realm `eap` imported from `realm-export.json`
  - Network: app → keycloak:8080/8443; public Route for browser redirects
- [ ] 10.8 **Documentation**: Document OIDC config, realm import, and secret handling; runbook for Keycloak on OpenShift

## Task Group 11: Frontend & Static Resources

- [ ] 11.1 Move webapp static assets into Quarkus `META-INF/resources`: `app/`, `partials/`, `bower_components/`, images, `coolstore.json`
- [ ] 11.2 Convert `index.jsp` (session-creating JSP shell) to a static `index.html` serving the AngularJS SPA; session need re-evaluated under TG6.2 cart strategy; remove `health.jsp` (replaced by `/q/health`)
- [ ] 11.3 Verify `coolstore.json` config (`MONOLITH`, `API_ENDPOINT`, `SSO_ENABLED`) still drives the SPA against the new service paths
- [ ] 11.4 **Test Generation**: Use mig-test-gen for the SPA shell
  - Command: `/skill mig-test-gen` targeting static resource serving
  - Expected: tests asserting `/`, `/app/...`, `/coolstore.json` return 200 and correct content types
- [ ] 11.5 **Containerization**: covered by TG1.8 image (static resources inside JAR)
- [ ] 11.6 **Deployment**: covered by TG13
- [ ] 11.7 **Documentation**: Document frontend hosting change (JSP → static) and any path rewrites

## Task Group 12: Containerization

- [ ] 12.1 Write multi-stage `Containerfile`/`Dockerfile`: Maven builder (JDK 21) → UBI minimal runtime with the Quarkus fast-jar; non-root user; JVM mode (native build optional stretch)
- [ ] 12.2 Push image to the OpenShift internal registry from the TG1.6 pipeline; enable image scanning (Trivy/Clair/OpenShift built-in) and fix findings
- [ ] 12.3 Verify container locally with Podman against dev PostgreSQL/Keycloak/Artemis (per README dev setup) before cluster deployment
- [ ] 12.4 **Test Generation**: Use mig-test-gen for the image
  - Command: `/skill mig-test-gen` targeting container smoke
  - Expected: smoke test — container boots, `/q/health/ready` green, cart→checkout journey passes
- [ ] 12.5 **Containerization**: Use mig-containerize to finalize and harden the image (this group's core)
  - Command: `/skill mig-containerize` for coolstore-quarkus
  - Config needs: all env from TG3/TG7/TG8/TG10 (datasource, broker, audit TCP, OIDC)
- [ ] 12.6 **Deployment**: Hand image reference to TG13 manifests
- [ ] 12.7 **Documentation**: Document image build, scan results, and local run instructions

## Task Group 13: OpenShift Deployment

- [ ] 13.1 Create manifests (or Helm chart): Deployment (≥2 replicas replacing the 2-node EAP cluster), Service, Route, resource requests/limits
- [ ] 13.2 Configure probes (`/q/health/live`, `/q/health/ready`), graceful shutdown, and rolling update strategy with rollback
- [ ] 13.3 Create ConfigMaps (JDBC URL, topic name, OIDC issuer, coolstore flags) and Secrets (DB creds, broker creds, audit TCP endpoint, Keycloak client secret); no secrets in image
- [ ] 13.4 Deploy supporting services: PostgreSQL (persistent or managed), Keycloak (realm `eap` imported), AMQ Broker (`topic/orders`); wire NetworkPolicies
- [ ] 13.5 Complete the `openshift` Maven profile from TG1.5 (image name, registry, build config/JVM args) and validate a build through it
- [ ] 13.6 Validate multi-replica behavior: cart state consistent across pods (TG6.2 strategy), JMS consumption distributed (pub/sub on both pods), rolling update without downtime
- [ ] 13.7 **Test Generation**: Use mig-test-gen for deployment validation
  - Command: `/skill mig-test-gen` targeting OpenShift smoke
  - Expected: pipeline stage — deploy → wait ready → run E2E suite (TG14) → teardown
- [ ] 13.8 **Containerization**: Consumes TG12 image; use mig-containerize outputs for env matrix
  - Command: `/skill mig-containerize` for coolstore-quarkus (final env/secret mapping)
- [ ] 13.9 **Deployment**: Use mig-deploy to produce and apply the manifests (this group's core)
  - Command: `/skill mig-deploy` for coolstore-quarkus + dependencies
  - Dependencies: image (TG12), PostgreSQL, Keycloak, AMQ Broker
  - Network: Route → app:8080; app → postgres:5432, keycloak, broker:61616, audit-collector TCP
- [ ] 13.10 **Documentation**: Document manifests, config/secrets inventory, scaling and rollback runbook

## Task Group 14: Verification, Performance & Cutover

- [ ] 14.1 Run full E2E journey on OpenShift: browse catalog → add/update/remove cart → Keycloak sign-in → checkout → order persisted → inventory decremented → async audit events streamed (charter from TG2)
- [ ] 14.2 Performance: compare startup time, memory, throughput vs. the JBoss baseline recorded in TG2.2; publish results
- [ ] 14.3 Observability: enable Quarkus metrics (`/q/metrics`) → OpenShift monitoring (Prometheus/Grafana dashboard), structured logging; alert rules for health + audit flow
- [ ] 14.4 Parallel run / cutover: route a percentage of traffic (canary), monitor 48h, then full cutover; keep JBoss rollback path for 30 days
- [ ] 14.5 Decommission: stop JBoss EAP nodes, remove legacy deployment scripts from README, archive baseline
- [ ] 14.6 Training session + final docs: Quarkus 3/OpenShift runbook for the team, updated README, migration decision log closed
- [ ] 14.7 **Test Generation**: Use mig-test-gen for the regression gate
  - Command: `/skill mig-test-gen` targeting E2E + performance suites
  - Expected: automated E2E suite, load-test scripts, acceptance report against success criteria
- [ ] 14.8 **Containerization**: final image scan sign-off (no High/Critical findings)
- [ ] 14.9 **Deployment**: cutover executed via mig-deploy outputs; rollback rehearsal documented
- [ ] 14.10 **Documentation**: close-out report — success criteria checklist, lessons learned
