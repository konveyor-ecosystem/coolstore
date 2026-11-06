# Migration Tasks

**Project:** CoolStore Monolith — Java EE 7 / JBoss EAP 7.4 → Quarkus 3 / Java 21 on Red Hat OpenShift

**Source artifacts:**
- Specification & design: `mig-prompt-workspace/migration-prompt.md`
- Knowledge graph: `.rgctl/` (1,232 files, 35,856 nodes, 125,562 edges; migration plan `hybrid_default`, scheduled order)
- Mandatory analysis rules: `rules/rule.yaml` (ruleset `audit-logging-migration`, 5 mandatory rules, target `quarkus3`/Java 21)

**Dependency order** (rgctl `migration_plan.json` app steps + community edges `rest→service→{utils,model}`, `utils→model`):
foundation → model/persistence → utils → service → rest → frontend → security → WebLogic removal → OpenShift cutover.
The legacy EAP WAR remains deployable until Task Group 9 cutover (rollback path).

---

## Task Group 1: Foundation — Quarkus 3 Skeleton, Java 21 & Dependency Baseline

- [ ] 1.1 Create Quarkus 3 Maven project structure: replace WAR packaging with JAR, add `quarkus-bom` (import) and `quarkus-maven-plugin` (build/package/goals), set `maven.compiler.release=21`; remove `javaee-web-api`/`javaee-api`/`jboss-jms-api`/`jboss-rmi-api` provided dependencies
- [ ] 1.2 Add Quarkus extensions: `quarkus-rest`, `quarkus-rest-jackson`, `quarkus-hibernate-orm`, `quarkus-flyway`, `quarkus-artemis-jms`, `quarkus-oidc`, `quarkus-smallrye-health`, `quarkus-micrometer`, `quarkus-container-image-jib`
- [ ] 1.3 Upgrade audit-logging-library 1.0.0 → 2.0.0 (rule `audit-logging-0001`): publish `lib/audit-logging-library-2.0.0.jar` to the local/internal Maven repository, replace the `system`-scoped dependency with a normal `com.enterprise:audit-logging-library:2.0.0` dependency, delete `lib/audit-logging-library-1.0.0.jar` and the `systemPath` entry
- [ ] 1.4 Create `src/main/resources/application.properties` with externalized configuration (no secrets in source): `quarkus.datasource.*` (PostgreSQL URL/user/password from env), `quarkus.artemis.*` (broker URL/credentials), `quarkus.oidc.*` (Keycloak auth-server URL, realm `eap`, client `eap-app`), audit-logger TCP endpoint
- [ ] 1.5 Replace the legacy Azure Web App GitHub Actions workflow (`.github/workflows/main_clustertesting(dev).yml`) with an OpenShift-targeted pipeline (build → container image → deploy), Java 21 toolchain
- [ ] 1.6 **Test Generation**: Use mig-test-gen to create build/boot smoke tests
  - Command: `/skill mig-test-gen` for the Quarkus application skeleton
  - Expected: `@QuarkusTest` smoke test asserting application context starts and `/q/health/live` returns 200
- [ ] 1.7 **Containerization**: Use mig-containerize to containerize the application
  - Command: `/skill mig-containerize` for coolstore-quarkus
  - Config needs: multi-stage build (Maven builder on JDK 21 → `registry.access.redhat.com/ubi9/openjdk-21` runtime), non-root user, env vars for `QUARKUS_DATASOURCE_URL`, `QUARKUS_DATASOURCE_USERNAME`/`PASSWORD` (Secret), `QUARKUS_ARTEMIS_URL`, `QUARKUS_OIDC_AUTH_SERVER_URL`, `AUDIT_LOGGER_TCP_ENDPOINT`
- [ ] 1.8 **Deployment**: Use mig-deploy to create deployment manifests
  - Command: `/skill mig-deploy` for coolstore-quarkus
  - Dependencies: PostgreSQL database, AMQ Broker, Keycloak (deployed in later groups; env-wired now)
  - Network: expose port 8080 (container) → Service → Route; liveness/readiness on `/q/health`
- [ ] 1.9 **Documentation**: Document the new build and runtime foundation
  - Update README with Quarkus build/run instructions (`mvn quarkus:dev`, `mvn package`)
  - Add configuration reference for all externalized properties

## Task Group 2: Model & Persistence Layer

- [ ] 2.1 Migrate model classes from `javax.*` to `jakarta.*` namespace: JPA entities `CatalogItemEntity`, `InventoryEntity`, `Order`, `OrderItem` and POJOs `Product`, `ShoppingCart`, `ShoppingCartItem`, `Promotion` (package `com.redhat.coolstore.model`); remove JAXB `@XmlRootElement` from `InventoryEntity` (JSON via Jackson)
- [ ] 2.2 Configure `quarkus-hibernate-orm` persistence unit replacing `src/main/resources/META-INF/persistence.xml` (`jta-data-source java:jboss/datasources/CoolstoreDS` → `quarkus.datasource` + `quarkus.hibernate-orm` properties); preserve Hibernate behavior flags (show_sql=false, format_sql=true)
- [ ] 2.3 Configure `quarkus-flyway` to run the existing migrations `src/main/resources/db/migration/V1_1__CreateSchema.sql` and `V1_2__AddInitialData.sql` at startup (`quarkus.flyway.migrate-at-start=true`); delete `utils/DataBaseMigrationStartup.java` (@Singleton @Startup EJB) — Flyway becomes Quarkus-managed
- [ ] 2.4 Replace `persistence/Resources.java` CDI `EntityManager` producer with `quarkus-hibernate-orm` managed `EntityManager` injection; add `@Transactional` boundaries on all persisting service methods (replaces EJB CMT)
- [ ] 2.5 Verify entity mappings against existing schema: `PRODUCT_CATALOG` (itemId PK, description text), `INVENTORY` (itemId PK, @OneToOne @PrimaryKeyJoinColumn), `ORDERS`, `ORDER_ITEMS` — no schema changes, no data migration
- [ ] 2.6 **Test Generation**: Use mig-test-gen for the persistence layer
  - Command: `/skill mig-test-gen` targeting `com.redhat.coolstore.model` and persistence configuration
  - Expected: repository/entity integration tests with Testcontainers PostgreSQL (catalog CRUD, order persist, inventory merge, Flyway baseline+migrate idempotency)
- [ ] 2.7 **Containerization**: Use mig-containerize for the PostgreSQL dependency
  - Command: `/skill mig-containerize` for postgresql (dev/test compose + image reference)
  - Config needs: `POSTGRES_USER`/`POSTGRES_PASSWORD`/`POSTGRES_DB` (Secret), volume for data, port 5432
- [ ] 2.8 **Deployment**: Use mig-deploy for PostgreSQL
  - Command: `/skill mig-deploy` for postgresql
  - Dependencies: persistent volume claim for PGDATA
  - Network: Service `postgresql:5432` consumed by the app via `QUARKUS_DATASOURCE_URL`
- [ ] 2.9 **Documentation**: Document persistence configuration
  - Data-layer runbook (Flyway behavior, baseline-on-migrate, backup/restore)
  - Schema reference (unchanged from V1_1/V1_2)

## Task Group 3: Utils & Cross-Cutting Concerns

- [ ] 3.1 Migrate `utils/Transformers.java`: replace `javax.json` (JSON-P) with `jakarta.json` or Jackson object mapping; preserve JSON contracts byte-for-byte (`shoppingCartToJson` order payload: orderValue, customerName, customerEmail, retailPrice, discount, shippingFee, shippingDiscount, items[]; `jsonToOrder` parsing; `toProduct` mapping)
- [ ] 3.2 Remove `utils/Producers.java` `java.util.logging.Logger` CDI producer; migrate `@Inject Logger` fields to JBoss Logging (`org.jboss.logging.Logger`) or SLF4J (already a transitive dep of audit-logging-library 2.0.0)
- [ ] 3.3 Replace `utils/StartupListener.java` (extends WebLogic `ApplicationLifecycleListener`) with a CDI bean observing Quarkus `StartupEvent`/`ShutdownEvent` (log `postStart`/`preStop` equivalents)
- [ ] 3.4 **Test Generation**: Use mig-test-gen for utils
  - Command: `/skill mig-test-gen` targeting `com.redhat.coolstore.utils`
  - Expected: unit tests for Transformers (cart→JSON→order round-trip, product mapping incl. missing-inventory warning path), startup event observer test
- [ ] 3.5 **Containerization**: Use mig-containerize to verify image integration
  - Command: `/skill mig-containerize` for coolstore-quarkus (rebuild; confirm utils changes)
  - Config needs: JSON logging to stdout (`quarkus.log.console.json=true`) for container log aggregation
- [ ] 3.6 **Deployment**: Use mig-deploy to update deployment config
  - Command: `/skill mig-deploy` for coolstore-quarkus (rolling update)
  - Dependencies: Task Group 1 image pipeline
  - Network: unchanged (port 8080)
- [ ] 3.7 **Documentation**: Document logging and JSON serialization changes
  - Note the JSON-P → Jackson/jakarta.json substitution and its contract tests
  - Runbook for structured log consumption

## Task Group 4: Service Layer — EJB → CDI, JMS & Audit Library 2.0

- [ ] 4.1 Convert `ShoppingCartService` from `@Stateful` EJB to CDI: `@ApplicationScoped` with cartId-keyed state store (`ConcurrentHashMap<String, ShoppingCart>`), preserving the `getShoppingCart(cartId)` / `checkOutShoppingCart(cartId)` API and multi-tab behavior noted in source
- [ ] 4.2 Eliminate remote EJB lookup: delete `lookupShippingServiceRemote()` (WildFlyInitialContextFactory JNDI `ejb:/ROOT/ShippingService!...`); inject `ShippingServiceRemote` locally via CDI (`@Inject`); convert `ShippingService` `@Stateless @Remote` → `@ApplicationScoped` CDI bean (keep interface)
- [ ] 4.3 Convert `ShoppingCartOrderProcessor` `@Stateless` → `@ApplicationScoped`; replace injected `JMSContext` + `@Resource(lookup = "java:/topic/orders")` with `quarkus-artemis-jms` producer sending `Transformers.shoppingCartToJson(cart)` to `topic/orders`
- [ ] 4.4 Convert `OrderServiceMDB` `@MessageDriven` → Artemis JMS `MessageListener` on `topic/orders` (preserve Auto-acknowledge, TextMessage body parse, order persist via `OrderService`, inventory deduction via `CatalogService.updateInventoryItems`)
- [ ] 4.5 Convert `InventoryNotificationMDB` from WebLogic JMS client (`WLInitialContextFactory`, `t3://localhost:7001`, `TopicConnection`) to an Artemis subscriber on `topic/orders`; preserve low-inventory threshold behavior (LOW_THRESHOLD=50)
- [ ] 4.6 Migrate `OrderService` audit logging to library 2.0.0 (rules `audit-logging-0002`–`0005`): `FileSystemAuditLogger` → `StreamableAuditLogger` over TCP (endpoint from config), `AuditEvent.builder()` → direct `AuditEvent` record instantiation, synchronous `logEvent`/`logSuccess`/`logFailure` → non-blocking `logEventAsync(event)`; `@PostConstruct`/`@PreDestroy` → CDI lifecycle (`@Observes StartupEvent` init, `@PreDestroy` close)
- [ ] 4.7 Convert remaining beans to CDI scopes with `@Transactional` persistence boundaries: `OrderService`, `CatalogService`, `ProductService` (`@Stateless` → `@ApplicationScoped`), `PromoService` (already `@ApplicationScoped`, add `jakarta` imports)
- [ ] 4.8 **Test Generation**: Use mig-test-gen for the service layer (characterization tests BEFORE behavioral changes)
  - Command: `/skill mig-test-gen` targeting `com.redhat.coolstore.service`
  - Expected: characterization tests — PromoService (25% off item 329299, free shipping at cart total ≥ 75), ShippingService tiers (2.99/4.99/6.99/8.99/10.99 by cart-total bands; insurance 2%/1.5%/1%), CartEndpoint dedupe logic; integration tests with Testcontainers Artemis + PostgreSQL — checkout → `topic/orders` → order persisted + inventory deducted + low-inventory alert; audit `logEventAsync` TCP stream verification
- [ ] 4.9 **Containerization**: Use mig-containerize for AMQ Broker
  - Command: `/skill mig-containerize` for amq-broker (Artemis)
  - Config needs: broker credentials (Secret), `topic/orders` auto-created, cluster password
- [ ] 4.10 **Deployment**: Use mig-deploy for AMQ Broker
  - Command: `/skill mig-deploy` for amq-broker
  - Dependencies: persistent volume for journal
  - Network: Service `amq-broker:61616` (core port) consumed via `QUARKUS_ARTEMIS_URL`
- [ ] 4.11 **Documentation**: Document service-layer changes
  - EJB → CDI mapping table, cart state design (cartId-keyed store, eviction strategy), JMS topology diagram, audit-logging 2.0.0 configuration guide

## Task Group 5: REST Layer

- [ ] 5.1 Replace `RestApplication` (`@ApplicationPath("/services")`) with `quarkus.rest.path=/services` configuration (or Jakarta `Application` subclass); delete `web.xml`
- [ ] 5.2 Migrate `CartEndpoint` `javax.ws.rs` → `jakarta.ws.rs`; convert `@SessionScoped` → `@RequestScoped` (state now lives in cartId-keyed `ShoppingCartService` from Task 4.1); preserve paths: `GET /cart/{cartId}`, `POST /cart/checkout/{cartId}`, `POST /cart/{cartId}/{itemId}/{quantity}`, `POST /cart/{cartId}/{tmpId}`, `DELETE /cart/{cartId}/{itemId}/{quantity}`
- [ ] 5.3 Migrate `OrderEndpoint` (`/orders`, `/orders/{orderId}`) and `ProductEndpoint` (`/products`, `/products/{itemId}`) to `jakarta.ws.rs` `@RequestScoped` resources; preserve JSON response shapes
- [ ] 5.4 Verify API contract compatibility against the AngularJS frontend consumers (`/services/cart`, `/services/products` from `src/main/webapp/app/services/*.js`)
- [ ] 5.5 **Test Generation**: Use mig-test-gen for REST endpoints
  - Command: `/skill mig-test-gen` targeting `com.redhat.coolstore.rest`
  - Expected: `@QuarkusTest` API characterization tests (status codes, JSON bodies, cart add/set/delete/dedupe flows, product list/detail, order list/detail) — recorded against legacy behavior
- [ ] 5.6 **Containerization**: Use mig-containerize to rebuild the application image
  - Command: `/skill mig-containerize` for coolstore-quarkus (REST layer included)
  - Config needs: unchanged; image tag promotion to test registry
- [ ] 5.7 **Deployment**: Use mig-deploy to expose the API
  - Command: `/skill mig-deploy` for coolstore-quarkus
  - Dependencies: PostgreSQL, AMQ Broker
  - Network: Route exposing `/services/*`; probes on `/q/health`
- [ ] 5.8 **Documentation**: Document the public API
  - Endpoint reference with request/response examples (unchanged contracts)
  - Cart-state semantics notes for API consumers

## Task Group 6: Frontend & Static Assets

- [ ] 6.1 Convert `src/main/webapp/index.jsp` → static `src/main/resources/META-INF/resources/index.html` (drop JSP session scriptlet; equivalent session handling via Keycloak token)
- [ ] 6.2 Copy static assets into `META-INF/resources/` preserving paths: `app/` (app.js, controllers, routes, services, directives, css, imgs), `bower_components/`, `partials/`, `coolstore.json`, `keycloak.json` (keycloak.js adapter config — auth-server URL externalized per environment)
- [ ] 6.3 Replace `health.jsp` with `quarkus-smallrye-health` endpoints (`/q/health/live`, `/q/health/ready`); remove JSP support dependency
- [ ] 6.4 **Test Generation**: Use mig-test-gen for frontend integration
  - Command: `/skill mig-test-gen` for the storefront E2E flow
  - Expected: E2E tests (browse catalog → Keycloak login → add to cart → checkout → order persisted → inventory updated); static asset serving tests
- [ ] 6.5 **Containerization**: Use mig-containerize to verify asset serving
  - Command: `/skill mig-containerize` for coolstore-quarkus (confirm static assets in image; image size budget for vendored bower libraries)
  - Config needs: keycloak.json per-environment overlay (ConfigMap)
- [ ] 6.6 **Deployment**: Use mig-deploy for the storefront
  - Command: `/skill mig-deploy` for coolstore-quarkus
  - Dependencies: Keycloak (Task Group 7) for login flow
  - Network: Route serving index.html at `/`
- [ ] 6.7 **Documentation**: Document frontend hosting changes
  - Static hosting layout, cache headers for bower assets, keycloak.json environment handling

## Task Group 7: Security — Keycloak OIDC Integration

- [ ] 7.1 Configure `quarkus-oidc` for bearer-token validation on `/services/*`: tenant `eap`, public client `eap-app`, auth-server URL from env; anonymous access only where the legacy app allowed it (public catalog browsing)
- [ ] 7.2 Deploy Keycloak (Red Hat build) on OpenShift importing `realm-export.json` (realm `eap`); externalize `auth-server-url` in `keycloak.json` for the frontend adapter
- [ ] 7.3 Verify frontend `keycloak.js` adapter login/logout flow (`src/main/webapp/app/app.js` Keycloak init) against the Quarkus OIDC validation
- [ ] 7.4 **Test Generation**: Use mig-test-gen for security
  - Command: `/skill mig-test-gen` targeting OIDC integration
  - Expected: integration tests — valid token → 200 on protected endpoints, missing/expired token → 401, Keycloak login E2E
- [ ] 7.5 **Containerization**: Use mig-containerize for Keycloak
  - Command: `/skill mig-containerize` for keycloak
  - Config needs: admin credentials (Secret), realm import mount, TLS
- [ ] 7.6 **Deployment**: Use mig-deploy for Keycloak
  - Command: `/skill mig-deploy` for keycloak
  - Dependencies: persistent volume for realm data
  - Network: Service `keycloak:8080` + Route; app consumes `QUARKUS_OIDC_AUTH_SERVER_URL`
- [ ] 7.7 **Documentation**: Document security architecture
  - OIDC flow diagram (keycloak.js → Keycloak → bearer token → quarkus-oidc), realm/client setup guide, token troubleshooting runbook

## Task Group 8: WebLogic Removal & Mandatory Rules Validation

- [ ] 8.1 Delete WebLogic shim packages `src/main/java/weblogic/application/*` (`ApplicationLifecycleListener`, `ApplicationLifecycleEvent`) and `src/main/java/weblogic/i18n/logging/*` (`NonCatalogLogger`)
- [ ] 8.2 Delete `utils/StartupListener.java` (superseded by Task 3.3 StartupEvent observer); confirm no source references `weblogic.*` remain (`grep -r weblogic src/`)
- [ ] 8.3 Remove obsolete descriptors and dependencies: `WEB-INF/web.xml` (`<distributable/>` → OpenShift replicas), `WEB-INF/beans.xml` (CDI implicit in Quarkus), `jboss-jms-api`/`jboss-rmi-api` leftovers; verify `pom.xml` contains no JBoss/WebLogic artifacts
- [ ] 8.4 Validate all 5 mandatory rules from `rules/rule.yaml` pass against the migrated codebase: `audit-logging-0001` (dependency 2.0.0 + Java 21), `0002` (no `AuditEvent.builder()`), `0003` (no `FileSystemAuditLogger`), `0004` (`logEventAsync` not `logEvent`), `0005` (no `logSuccess`/`logFailure`)
- [ ] 8.5 **Test Generation**: Use mig-test-gen for regression coverage
  - Command: `/skill mig-test-gen` for post-cleanup regression
  - Expected: full test suite green after shim deletion (unit + integration + E2E)
- [ ] 8.6 **Containerization**: Use mig-containerize for the final image
  - Command: `/skill mig-containerize` for coolstore-quarkus (final image; security scan, size check, no weblogic classes)
  - Config needs: image signing/scanning gate in pipeline
- [ ] 8.7 **Deployment**: Use mig-deploy to verify manifests
  - Command: `/skill mig-deploy` for coolstore-quarkus (config audit)
  - Dependencies: none new
  - Network: confirm no WebLogic/JBoss endpoints (t3://, ejb:/) remain in any manifest or config
- [ ] 8.8 **Documentation**: Document legacy decoupling
  - Migration guide: WebLogic/EAP feature → Quarkus equivalent mapping table; rules compliance report

## Task Group 9: OpenShift Deployment, Observability & Cutover

- [ ] 9.1 Wire health probes: liveness `/q/health/live`, readiness `/q/health/ready` (incl. datasource + Artemis + OIDC checks) into Deployment; startup probe for JVM warm-up
- [ ] 9.2 Configure workload resources: CPU/memory requests and limits, HorizontalPodAutoscaler (min 2 replicas to replace two-node EAP cluster), rolling update strategy (maxUnavailable 0)
- [ ] 9.3 Observability: `quarkus-micrometer` Prometheus metrics endpoint, JSON structured logs, dashboards for checkout/order-flow rates and inventory thresholds
- [ ] 9.4 Full-stack OpenShift deployment: coolstore-quarkus + PostgreSQL + AMQ Broker + Keycloak with ConfigMaps/Secrets per Task Groups 1–8
- [ ] 9.5 Parallel run & rollback rehearsal: legacy EAP `ROOT.war` stays deployable; verify rollback procedure and data (Flyway schema untouched)
- [ ] 9.6 Performance validation: baseline legacy EAP vs Quarkus (latency/throughput on catalog/cart/checkout); target equal-or-better and improved startup
- [ ] 9.7 Cutover: switch traffic to OpenShift Route, monitor, then decommission legacy EAP hosts and the Azure deployment pipeline
- [ ] 9.8 **Test Generation**: Use mig-test-gen for production readiness
  - Command: `/skill mig-test-gen` for E2E + performance on OpenShift
  - Expected: E2E business workflow on-cluster (browse → login → cart → checkout → order persisted → inventory updated → audit event streamed), load test meeting baseline
- [ ] 9.9 **Containerization**: Use mig-containerize for final registry promotion
  - Command: `/skill mig-containerize` for coolstore-quarkus (production tag, scan report, OpenShift internal registry)
  - Config needs: production Secret/ConfigMap set
- [ ] 9.10 **Deployment**: Use mig-deploy for production
  - Command: `/skill mig-deploy` for the full stack (production namespace)
  - Dependencies: PostgreSQL, AMQ Broker, Keycloak, image registry
  - Network: Route with TLS; internal Services only for DB/broker/IdP
- [ ] 9.11 **Documentation**: Complete operations documentation
  - Runbook (deploy, scale, rollback), architecture diagram, configuration reference, monitoring/alerting guide, team training plan for Quarkus 3 / Java 21 / OpenShift

---

**Traceability:** Task Groups 2–6 implement rgctl migration plan steps 15–21/44/46/89 (model → utils → service → rest → webapp.app); Task Group 8 covers steps 33/40 (weblogic shims) and step 48 (persistence cleanup); Task Groups 1, 7, 9 implement migration-prompt Phases 1 and 6 (foundation, platform & cutover).
