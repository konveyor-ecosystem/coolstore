# Migration Tasks

CoolStore Monolith: Java EE 7 / JBoss EAP 7.4 → Quarkus 3 / Java 21 on OpenShift.
Source of truth: `mig-prompt-workspace/migration-prompt.md` (spec + design) and rgctl knowledge graph (`.rgctl/migration_plan.json`, 153 steps, package order: `weblogic.*` shims → `coolstore.model` → `coolstore.persistence` → `coolstore.rest` → `coolstore.service` → `coolstore.utils`).

## Task Group 1: Foundation & Build Modernization

- [x] 1.1 Restructure `pom.xml`: add Quarkus 3.x BOM (`quarkus-bom`), switch packaging to `quarkus-maven-plugin` (fast-jar), remove `maven-war-plugin`/`javaee-web-api`/`javaee-api`/`jboss-jms-api`/`jboss-rmi-api` dependencies
- [x] 1.2 Upgrade compiler to Java 21 (`maven.compiler.release=21`)
- [x] 1.3 Add Quarkus extensions: `quarkus-rest`, `quarkus-hibernate-orm`, `quarkus-jdbc-postgresql`, `quarkus-flyway`, `quarkus-oidc`, `quarkus-messaging-amqp`, `quarkus-arc`, `quarkus-smallrye-health`, `quarkus-micrometer`
- [x] 1.4 Vendor `lib/audit-logging-library-2.0.0.jar` into the Maven build (system scope is not container-friendly): install to project-local repo or `maven-install-plugin`, remove 1.0.0 dependency
- [x] 1.5 Create `src/main/resources/application.properties` skeleton (datasource, OIDC, messaging placeholders — externalized via env vars for OpenShift)
- [x] 1.6 Fix all 5 mandatory Konveyor rules (`rules/rule.yaml`, `audit-logging-0001..0005`) in `OrderService.java`: `FileSystemAuditLogger` → `StreamableAuditLogger` (TCP), `AuditEvent` record instantiation instead of Builder, `logEventAsync(event)` instead of `logEvent`/`logSuccess`/`logFailure`; audit log directory `./device-inventory-audit-logs` config → TCP endpoint from config/Secret
- [x] 1.7 Set up CI pipeline (OpenShift Pipelines/Tekton): build, test, image build + scan
- [x] 1.8 **Test Generation**: Use mig-test-gen to create characterization tests for current behavior
  - Command: `/skill mig-test-gen` for pricing/promotions (shipping tiers 2.99–10.99, promo `329299` 25% off, free shipping ≥ 75, insurance 2%/1.5%/1%), `Transformers.shoppingCartToJson` output shape, and audit-logging v2 async emission
  - Expected: Unit tests for pricing rules and JSON transformers; characterization test suite that passes on EAP baseline (captured behavior) and on Quarkus
- [x] 1.9 **Containerization**: Use mig-containerization to produce the base build assets
  - Command: `/skill mig-containerization` for coolstore-monolith (multi-stage Containerfile: Maven build → UBI9 OpenJDK 21 runtime, fast-jar)
  - Config needs: env vars `QUARKUS_DATASOURCE_*`, `QUARKUS_OIDC_*`, `AUDIT_TCP_ENDPOINT` (secret)
- [x] 1.10 **Deployment**: Use mig-deploy to scaffold the OpenShift project layout
  - Command: `/skill mig-deploy` for coolstore-monolith (namespace, image stream, build pipeline)
  - Dependencies: none (Phase 1 targets dev namespace only)
- [x] 1.11 **Documentation**: Document the new build
  - Update README: Quarkus dev mode (`mvn quarkus:dev`), local run prerequisites
  - Add `CONTRIBUTING`/runbook notes on Java 21 + Podman prerequisites

## Task Group 2: Remove WebLogic-Specific Code (rgctl schedule step 9/48)

- [x] 2.1 Delete `src/main/java/weblogic/application/ApplicationLifecycleEvent.java`, `ApplicationLifecycleListener.java`, `weblogic/i18n/logging/NonCatalogLogger.java` (in-repo shims)
- [x] 2.2 Replace `utils/StartupListener` (extends WebLogic lifecycle listener) with a Quarkus `@Observes StartupEvent`/`ShutdownEvent` CDI observer
- [x] 2.3 Remove the WebLogic JMS client path in `service/InventoryNotificationMDB.java` (`WLInitialContextFactory`, `t3://localhost:7001`, `TCF`, `PortableRemoteObject`); keep the low-inventory threshold logic (LOW_THRESHOLD=50) for re-implementation in Task Group 5
- [x] 2.4 Verify no `weblogic.*` imports remain (`rgctl -f json find "weblogic" -l java` / `rg "import weblogic" src/`)
- [x] 2.5 **Test Generation**: Use mig-test-gen for startup behavior
  - Command: `/skill mig-test-gen` targeting the startup observer
  - Expected: Quarkus `@QuarkusTest` verifying startup event fires (log line) and Flyway runs
- [x] 2.6 **Documentation**: Record removed WebLogic shims and their replacements in the migration runbook

## Task Group 3: Migrate Persistence & Domain Model (rgctl community `coolstore.model` step 17, `coolstore.persistence` step 55)

- [x] 3.1 Migrate entities `CatalogItemEntity`, `InventoryEntity`, `Order`, `OrderItem` from `javax.persistence` → `jakarta.persistence` (annotations unchanged otherwise); keep table names `PRODUCT_CATALOG`, `INVENTORY`, `ORDERS`, `ORDER_ITEMS`
- [x] 3.2 Migrate POJOs `Product`, `Promotion`, `ShoppingCart`, `ShoppingCartItem` to plain CDI-friendly POJOs; add JSON-B annotations where the REST contract requires (verify against characterization tests)
- [x] 3.3 Replace `persistence.xml` (PU `primary`, JTA `java:jboss/datasources/CoolstoreDS`) with `application.properties` (`quarkus.datasource.jdbc.url` etc. → PostgreSQL via env vars; `quarkus-hibernate-orm.database.generation=none`)
- [x] 3.4 Replace `persistence/Resources.java` (`@PersistenceContext @Produces EntityManager`) with direct `@Inject EntityManager` (Hibernate ORM managed by Quarkus); keep `utils/Producers.java` Logger producer or switch to Quarkus/JBoss Logging
- [x] 3.5 Migrate Flyway: remove `utils/DataBaseMigrationStartup` (`@Singleton @Startup`, `@Resource mappedName=java:jboss/datasources/CoolstoreDS`) → `quarkus-flyway` running `db/migration/V1_1__CreateSchema.sql` and `V1_2__AddInitialData.sql` unchanged (baseline-on-migrate for existing DB)
- [x] 3.6 **Test Generation**: Use mig-test-gen for persistence layer
  - Command: `/skill mig-test-gen` targeting model/persistence (Testcontainers PostgreSQL)
  - Expected: Repository tests for `CatalogService`-equivalent queries, order persistence with eager `@OneToMany` items, inventory merge updates
- [x] 3.7 **Documentation**: Document datasource configuration (env vars, Secrets on OpenShift) and Flyway baseline strategy

## Task Group 4: Migrate Business Services — EJB → CDI (rgctl community `coolstore.service` step 93, `coolstore.utils` step 95)

- [x] 4.1 `ShoppingCartService` (`@Stateful`) → `@ApplicationScoped` CDI bean with cart state keyed by cartId (in-memory map first; externalized state noted as risk R1) — remove `lookupShippingServiceRemote()` JNDI code (`WildFlyInitialContextFactory`, `ejb:/ROOT/ShippingService!`) and `@Inject` `ShippingService` directly
- [x] 4.2 `CatalogService`, `ProductService`, `OrderService`, `ShippingService`, `ShoppingCartOrderProcessor` (`@Stateless`) → `@ApplicationScoped` CDI beans; `@PostConstruct`/`@PreDestroy` lifecycle retained for `OrderService` audit logger init/close
- [x] 4.3 `PromoService` (`@ApplicationScoped`, Serializable) → plain `@ApplicationScoped` CDI bean (remove `Serializable`/serialVersionUID)
- [x] 4.4 `ShippingService`: remove `@Remote` EJB interface; collapse `ShippingServiceRemote` into the bean (local interface or direct class)
- [x] 4.5 Replace `System.out.println` logging in `OrderServiceMDB`/`InventoryNotificationMDB` bodies with a proper logger (JBoss Logging/SLF4J)
- [x] 4.6 Replace `javax.json` usage in `utils/Transformers.java` with Jakarta JSON-P/JSON-B, preserving the exact order JSON contract (orderValue, customerName, customerEmail, retailPrice, discount, shippingFee, shippingDiscount, items[productSku, quantity]) — verify with characterization tests
- [x] 4.7 **Test Generation**: Use mig-test-gen for service layer
  - Command: `/skill mig-test-gen` targeting services (cart pricing, checkout, shipping, promos, audit logging)
  - Expected: Unit tests for all pricing paths; tests that audit events are emitted asynchronously via `StreamableAuditLogger` (TCP test container or mock)
- [x] 4.8 **Documentation**: Document EJB→CDI mapping decisions and cart-state behavior in the runbook

## Task Group 5: Migrate Messaging — JMS → Reactive Messaging

- [x] 5.1 Configure `quarkus-messaging-amqp` against an AMQP broker (Artemis/AMQ) on OpenShift; define `topic/orders` equivalent address
- [x] 5.2 `ShoppingCartOrderProcessor.process(cart)` → `@Emitter`/`MutinyEmitter<String>` publishing the order JSON (replacing injected `JMSContext` + `@Resource Topic java:/topic/orders`)
- [x] 5.3 `OrderServiceMDB` (`@MessageDriven` on `topic/orders`, auto-acknowledge) → `@Incoming("orders")` consumer: parse order JSON, `OrderService.save(order)`, update inventory per item (preserve current onMessage semantics incl. exception → rollback/re-delivery)
- [x] 5.4 Re-implement `InventoryNotificationMDB` as a second `@Incoming` consumer on the same topic preserving low-inventory alert logic (threshold 50, "contact supplier" log)
- [x] 5.5 Document delivery semantics mapping (JMS auto-acknowledge/durable topic vs. AMQP) and confirm fan-out to both consumers
- [x] 5.6 **Test Generation**: Use mig-test-gen for messaging
  - Command: `/skill mig-test-gen` targeting the messaging flow
  - Expected: Integration tests (embedded/in-memory broker or Testcontainers Artemis): publish order → both consumers receive; order persisted; inventory decremented; low-threshold alert raised
- [x] 5.7 **Deployment**: Use mig-deploy for the AMQP broker dependency
  - Command: `/skill mig-deploy` for broker (AMQ/Artemis template or operator) + Service/Secret wiring to the app
  - Dependencies: OpenShift namespace from Task Group 1
  - Network: app → broker AMQP port 5672
- [x] 5.8 **Documentation**: Document broker topology and consumer semantics in the runbook

## Task Group 6: Migrate REST API & Web Tier (rgctl community `coolstore.rest` step 70, webapp JS 49–101)

- [x] 6.1 Migrate JAX-RS `RestApplication` (`@ApplicationPath("/services")`) → Quarkus REST (drop-in `Application` or `quarkus.rest.path=/services`); preserve base path `/services`
- [x] 6.2 Migrate `CartEndpoint` (`@SessionScoped` → `@RequestScoped` or Quarkus `@ApplicationScoped` with cart-id path semantics preserved): routes `GET /cart/{cartId}`, `POST /cart/checkout/{cartId}`, `POST /cart/{cartId}/{itemId}/{quantity}`, `POST /cart/{cartId}/{tmpId}`, `DELETE /cart/{cartId}/{itemId}/{quantity}`; `OrderEndpoint` (`GET /orders`, `GET /orders/{orderId}`); `ProductEndpoint` (`GET /products`, `GET /products/{itemId}`) — `javax.ws.rs` → `jakarta.ws.rs`
- [x] 6.3 Convert `src/main/webapp/index.jsp` and `health.jsp` to static resources under `META-INF/resources` (JSP not supported on Quarkus); replace `health.jsp` with SmallRye Health (`/q/health`)
- [x] 6.4 Serve the AngularJS app (`app/`, `partials/`, `bower_components/`, `coolstore.json`) as static resources; keep the UI out of scope for framework changes
- [x] 6.5 Replace the Keycloak JS adapter (`keycloak.json`, realm `eap`, client `eap-app`, public) with OIDC via `quarkus-oidc` (Bearer + web-app policy for the SPA); update front-end login flow to OIDC redirect; deploy `realm-export.json` realm to the Keycloak instance on OpenShift
- [x] 6.6 **Test Generation**: Use mig-test-gen for REST layer
  - Command: `/skill mig-test-gen` targeting REST endpoints
  - Expected: `@QuarkusTest` contract tests for all 9 operations (paths, JSON shapes, status codes) mirroring the characterization baseline; OIDC-secured endpoint tests (401 without token, 200 with test token)
- [x] 6.7 **Documentation**: Update API docs; document the static-asset layout and OIDC flow

## Task Group 7: Containerization & OpenShift Deployment

- [x] 7.1 Finalize multi-stage `Containerfile` (UBI9 OpenJDK 21 runtime, non-root user, fast-jar); build and push to OpenShift internal registry
- [x] 7.2 Create OpenShift manifests: Deployment (2+ replicas, resource requests/limits, rolling strategy), Service, Route, HPA
- [x] 7.3 Liveness/readiness probes on `/q/health/live` and `/q/health/ready`; startup probe
- [x] 7.4 ConfigMaps for non-secret config; Secrets for DB credentials (replace README `postgresUser/postgresPW`), Keycloak client secret, audit-log TCP endpoint
- [x] 7.5 Deploy supporting services: PostgreSQL (operator/template), Keycloak (SSO operator/template + realm import), AMQP broker (from Task Group 5)
- [x] 7.6 Fill in the pom.xml `<!-- TODO: Add OpenShift profile here -->` profile (container-image build/deploy)
- [x] 7.7 **Containerization**: Use mig-containerization to validate readiness and scan the image
  - Command: `/skill mig-containerization` for coolstore-monolith (verify: no filesystem-path audit logging, no localhost-hardcoded endpoints, secrets externalized — graph findings show localhost references in keycloak.json and WebLogic JMS URL to be removed)
  - Config needs: env vars for datasource, OIDC auth URL, broker URL, audit TCP endpoint
- [x] 7.8 **Deployment**: Use mig-deploy for the full application stack
  - Command: `/skill mig-deploy` for coolstore-monolith
  - Dependencies: PostgreSQL, Keycloak, AMQP broker
  - Network: expose Route → app 8080; app → PostgreSQL 5432, Keycloak 8080, broker 5672
- [x] 7.9 **Test Generation**: Use mig-test-gen for deployment smoke tests
  - Command: `/skill mig-test-gen` for smoke suite against the Route (health, catalog, cart flow)
  - Expected: Post-deploy smoke test job validating `/q/health/ready`, `/services/products`, and an authenticated checkout
- [x] 7.10 **Documentation**: Runbook for deploy/rollback on OpenShift; update README (remove manual JBoss/podman local-DB instructions in favor of `oc` + Quarkus dev)

## Task Group 8: Verification, Parallel Run & Cutover

- [x] 8.1 Run the full characterization suite against the Quarkus deployment; diff behavior vs. EAP baseline (REST contracts, order JSON, pricing)
- [ ] 8.2 Performance test: baseline vs. new (startup time, memory, throughput on checkout flow)
- [x] 8.3 Security validation: image scan clean; no secrets in manifests; verify all 5 Konveyor audit-logging rules pass (`rgctl rules`); no `javax.*`/`weblogic.*` imports remain
- [ ] 8.4 Parallel run: Quarkus deployment live alongside EAP (schema shared read-only validation or replica DB); monitor 2 weeks
- [ ] 8.5 Cutover traffic to OpenShift Route; keep EAP rollback path for 30 days; decommission EAP 7.4
- [x] 8.6 **Documentation**: Cutover runbook, monitoring/observability guide (`/q/metrics` Micrometer → OpenShift monitoring), and final migration report
