# Migration Tasks: CoolStore Monolith → Quarkus 3.x on OpenShift

> **Source:** Java EE 7 WAR (JBoss EAP / WildFly) · Java 8  
> **Target:** Quarkus 3.x · Java 17 · Red Hat OpenShift 4.x  
> **Strategy:** Phased Strangler Fig — model → persistence → service → messaging → REST → containerise → cutover  
> **Graph order (rgctl hybrid_default):** model (step 17) → persistence (step 49) → rest (step 70) → service (step 93) → utils (step 95)

---

## Task Group 1: Foundation & Project Scaffolding

> Establish the Quarkus project skeleton, CI/CD pipeline, and secure the codebase before any migration work begins.

- [ ] 1.1 Rotate and remove the **3 embedded secrets** identified by rgctl security scan; inject replacements via environment variables referencing OpenShift Secrets
- [ ] 1.2 Scaffold a new Quarkus 3.x Maven project alongside the monolith in a `coolstore-quarkus/` subdirectory with extensions: `resteasy-reactive-jackson`, `hibernate-orm-panache`, `flyway`, `oidc`, `smallrye-reactive-messaging-amqp`, `health`, `micrometer-registry-prometheus`
- [ ] 1.3 Configure Quarkus `application.properties` for dev-mode: PostgreSQL datasource (`%dev` profile), AMQ Artemis broker (`%dev`), Keycloak OIDC (`%dev` Keycloak dev service)
- [ ] 1.4 Migrate `keycloak.json` realm configuration to `quarkus.oidc.*` properties; validate against existing `realm-export.json` — user/role mappings must be identical
- [ ] 1.5 Perform automated **`javax.*` → `jakarta.*` namespace migration** across all Java sources using `jakarta-migration` tool or OpenRewrite recipe `org.openrewrite.java.migrate.jakarta.JavaxMigrationToJakarta`
- [ ] 1.6 Set up **OpenShift Pipelines (Tekton)** pipeline with stages: `build` → `unit-test` → `image-build` → `image-scan` → `deploy-dev` → `integration-test` → `promote-staging`
- [ ] 1.7 Configure Git branch protection and PR-gated pipeline execution
- [ ] 1.8 **Test Generation**: Use mig-test-gen to create characterisation test suite against the running monolith REST API
  - Target: `CartEndpoint`, `ProductEndpoint`, `OrderEndpoint`
  - Expected: REST-assured tests capturing all 12 endpoint contracts (request/response shapes, status codes)
  - Run against monolith before any migration; commit as golden baseline
- [ ] 1.9 **Documentation**: Record Architectural Decision Records (ADRs) for: Quarkus over Spring Boot, Strangler Fig pattern, namespace migration approach, secret rotation strategy

---

## Task Group 2: Model Layer Migration (`com.redhat.coolstore.model`)

> Migrate all JPA entity and DTO classes; lowest harmonic centrality — migrate first (rgctl step 17).

- [ ] 2.1 Create `src/main/java/com/redhat/coolstore/model/` in Quarkus project; copy and update all 8 model classes: `CatalogItemEntity`, `InventoryEntity`, `Order`, `OrderItem`, `Product`, `Promotion`, `ShoppingCart`, `ShoppingCartItem`
- [ ] 2.2 Update all imports from `javax.persistence.*` → `jakarta.persistence.*`, `javax.json.*` → `jakarta.json.*`
- [ ] 2.3 Annotate `CatalogItemEntity` and `InventoryEntity` for Panache: extend `PanacheEntityBase` or use repository pattern; add `@Entity`, `@Table` annotations if not already present
- [ ] 2.4 Annotate `Order` and `OrderItem` with Panache entity annotations; ensure `@OneToMany` / `@ManyToOne` JPA relationships are correctly mapped for Jakarta EE 10
- [ ] 2.5 Verify `ShoppingCart` and `ShoppingCartItem` as non-entity DTOs (no persistence annotations); adapt for Quarkus JSON serialisation with Jackson (`@JsonProperty` if needed)
- [ ] 2.6 Verify `Promotion` as a plain Java object; no persistence annotation changes needed
- [ ] 2.7 Run Flyway migration scripts (from `src/main/resources/db/migration/`) against a local PostgreSQL instance to validate schema compatibility with migrated entity mappings
- [ ] 2.8 **Test Generation**: Use mig-test-gen for model layer
  - Expected: Unit tests for `ShoppingCart.addShoppingCartItem()`, `ShoppingCart.removeShoppingCartItem()`, `ShoppingCart.resetShoppingCartItemList()`; `Transformers.toProduct()`, `Transformers.shoppingCartToJson()`, `Transformers.jsonToOrder()`
  - Minimum 80% line coverage on all model classes
- [ ] 2.9 **Documentation**: Document entity relationship diagram; note any schema deviations from original

---

## Task Group 3: Persistence Layer Migration (`com.redhat.coolstore.persistence`)

> Migrate CDI producers for `EntityManager`; replace JNDI datasource with Quarkus datasource config (rgctl step 49).

- [ ] 3.1 Remove `Resources.java` CDI `EntityManager` producer — Quarkus injects `EntityManager` directly via `@Inject` with Panache or `@PersistenceContext` (Quarkus supports both)
- [ ] 3.2 Update `application.properties`: replace JNDI datasource `java:jboss/datasources/CoolstoreDS` with `quarkus.datasource.jdbc.url`, `quarkus.datasource.username`, `quarkus.datasource.password` backed by OpenShift Secrets
- [ ] 3.3 Migrate Flyway from `DataBaseMigrationStartup` `@Singleton @Startup` EJB to `quarkus.flyway.*` extension configuration (`quarkus.flyway.migrate-at-start=true`, `quarkus.flyway.baseline-on-migrate=true`)
- [ ] 3.4 Verify Flyway SQL migration scripts under `src/main/resources/db/migration/` are compatible with Flyway 9.x (checksum recomputation, `flyway repair` if needed)
- [ ] 3.5 Delete `DataBaseMigrationStartup.java` — functionality fully replaced by Quarkus Flyway extension
- [ ] 3.6 Delete `Producers.java` CDI Logger producer — replace with `@Inject` `org.jboss.logging.Logger` (Quarkus built-in) or `private static final Logger LOG = Logger.getLogger(ClassName.class)`
- [ ] 3.7 **Test Generation**: Use mig-test-gen for persistence layer
  - Expected: `@QuarkusTest` integration tests with `@QuarkusTestResource(PostgreSQLTestResource.class)` (Testcontainers); verify Flyway baseline applies cleanly; verify `EntityManager` injection works
- [ ] 3.8 **Documentation**: Document datasource configuration migration; note Flyway version compatibility decisions

---

## Task Group 4: Utility & Lifecycle Migration (`com.redhat.coolstore.utils`)

> Replace WebLogic lifecycle hooks and proprietary audit logger; migrate Transformers (rgctl step 95).

- [ ] 4.1 Delete `weblogic/application/ApplicationLifecycleEvent.java` and `weblogic/application/ApplicationLifecycleListener.java` stub classes
- [ ] 4.2 Rewrite `StartupListener.java`: replace `extends ApplicationLifecycleListener` with a CDI `@ApplicationScoped` bean observing Quarkus `StartupEvent` / `ShutdownEvent` via `void onStart(@Observes StartupEvent ev)` / `void onStop(@Observes ShutdownEvent ev)`
- [ ] 4.3 **Proprietary audit library replacement**: Create `AuditService.java` that replicates `FileSystemAuditLogger` behaviour from `com.enterprise:audit-logging-library:1.0.0`:
  - Writes structured audit records to `./device-inventory-audit-logs/` directory (preserve log format by inspecting JAR bytecode with `javap` / Fernflower decompiler)
  - Expose as `@ApplicationScoped` CDI bean
  - Log to both filesystem and Quarkus structured logging (JSON) for OpenShift observability
- [ ] 4.4 Update `OrderService.java` to `@Inject AuditService` instead of `FileSystemAuditLogger`; remove `@PostConstruct`/`@PreDestroy` audit logger lifecycle methods
- [ ] 4.5 Migrate `Transformers.java`: update `javax.json.*` imports to `jakarta.json.*`; verify JSON serialisation/deserialisation correctness against characterisation test golden output
- [ ] 4.6 Remove `system`-scoped Maven dependency `com.enterprise:audit-logging-library:1.0.0` from `pom.xml` once `AuditService` is complete and tested
- [ ] 4.7 **Test Generation**: Use mig-test-gen for utils layer
  - Expected: Unit tests for `Transformers.toProduct()` round-trip, `Transformers.shoppingCartToJson()` JSON schema validation, `Transformers.jsonToOrder()` parse; `AuditService` write-to-filesystem unit test with temporary directory
- [ ] 4.8 **Documentation**: Document audit logger replacement decision; create runbook for rotating audit log directory on OpenShift (PVC vs emptyDir); update ADR

---

## Task Group 5: Service Layer Migration (`com.redhat.coolstore.service`)

> Migrate EJB services to CDI beans; replace `@Stateful` with `@SessionScoped`; keep `ShippingService` local (rgctl step 93).

- [ ] 5.1 Migrate `CatalogService.java`: remove `@Stateless`; annotate `@ApplicationScoped`; replace `@Inject EntityManager em` with Panache repository or keep `@Inject EntityManager` (Quarkus supports); update JPA criteria API imports to `jakarta.persistence.*`; annotate mutating methods with `@Transactional`
- [ ] 5.2 Migrate `OrderService.java`: remove `@Stateless`; annotate `@ApplicationScoped`; replace `EntityManager` JPA operations with Panache; remove EJB `@PostConstruct`/`@PreDestroy` audit logger lifecycle (delegated to `AuditService`); inject `AuditService`; annotate `save()` with `@Transactional`
- [ ] 5.3 Migrate `ProductService.java`: remove `@Stateless`; annotate `@ApplicationScoped`; no other structural changes needed
- [ ] 5.4 Migrate `PromoService.java`: remove `@ApplicationScoped` (already CDI — no change needed); verify `implements Serializable` is retained; no EJB-specific constructs to remove
- [ ] 5.5 Migrate `ShippingService.java`: remove `@Stateless @Remote`; annotate `@ApplicationScoped`; remove `ShippingServiceRemote` interface (now local CDI bean); update `ShoppingCartService` injection to use direct `@Inject ShippingService`
- [ ] 5.6 Migrate `ShoppingCartService.java`: this is the most complex migration:
  - Replace `@Stateful` → `@SessionScoped` (Quarkus supports HTTP session scope via `quarkus-undertow` or `quarkus-resteasy-reactive`)
  - Remove WebLogic JNDI lookup (`WildFlyInitialContextFactory`, `ejb:/ROOT/ShippingService`) — `ShippingService` is now a local CDI bean
  - Replace `ShippingServiceRemote lookupShippingServiceRemote()` static method with `@Inject ShippingService shippingService`
  - Annotate `checkOutShoppingCart()` with `@Transactional`
- [ ] 5.7 Migrate `ShoppingCartOrderProcessor.java`: remove `@Stateless`; annotate `@ApplicationScoped`; replace JMS `@Inject JMSContext` / `@Resource Topic ordersTopic` with SmallRye Reactive Messaging `@Channel("orders") Emitter<String> ordersEmitter`; update `process()` to call `ordersEmitter.send(Transformers.shoppingCartToJson(cart))`
- [ ] 5.8 Delete `ShippingServiceRemote.java` interface — no longer needed
- [ ] 5.9 **Test Generation**: Use mig-test-gen for service layer
  - Expected: Unit tests for `PromoService.applyCartItemPromotions()` (25% discount on item 329299), `PromoService.applyShippingPromotions()` (free shipping over $75), `ShippingService.calculateShipping()` and `calculateShippingInsurance()` for all price bands; `@QuarkusTest` for `CatalogService`, `OrderService`, `ProductService` with Testcontainers PostgreSQL; mock `ShoppingCartOrderProcessor` for `ShoppingCartService` unit tests
- [ ] 5.10 **Documentation**: Document EJB-to-CDI mapping decisions; document session scope strategy choice (HTTP session vs stateless + DB-backed cart); update ADR

---

## Task Group 6: Messaging Migration — MDB Replacement

> Replace `@MessageDriven` MDBs and WebLogic T3 JMS with SmallRye Reactive Messaging over AMQ Artemis.

- [ ] 6.1 Deploy **AMQ Broker** on OpenShift using the AMQ Broker Operator; configure `topic/orders` as an address with `multicast` routing type (pub/sub semantics matching JMS Topic)
- [ ] 6.2 Add `quarkus-smallrye-reactive-messaging-amqp` extension to `pom.xml`; configure AMQP connector in `application.properties`: `mp.messaging.connector.smallrye-amqp.host`, `port`, `username`, `password` (from OpenShift Secret)
- [ ] 6.3 Migrate `OrderServiceMDB.java`: delete `@MessageDriven` and `@ActivationConfigProperty` annotations; rewrite as `@ApplicationScoped` CDI bean with `@Incoming("orders")` method consuming `String` messages from the AMQP `orders` channel; preserve business logic (parse order, persist via `OrderService`, update inventory via `CatalogService`)
- [ ] 6.4 Migrate `InventoryNotificationMDB.java`: delete entire WebLogic T3 JNDI setup (`WLInitialContextFactory`, `t3://localhost:7001`, `TopicConnectionFactory`, `PortableRemoteObject`); rewrite as `@ApplicationScoped` CDI bean with `@Incoming("orders-inventory")` on the same AMQP topic (second consumer group); preserve low-stock threshold logic
- [ ] 6.5 Configure SmallRye channel mappings: `mp.messaging.incoming.orders.connector=smallrye-amqp`, `mp.messaging.incoming.orders.address=topic/orders`; `mp.messaging.outgoing.orders.connector=smallrye-amqp`, `mp.messaging.outgoing.orders.address=topic/orders`
- [ ] 6.6 Validate message ordering and at-least-once delivery semantics; configure dead-letter address for failed messages
- [ ] 6.7 **Test Generation**: Use mig-test-gen for messaging layer
  - Expected: `@QuarkusTest` with `@QuarkusTestResource(ArtemisTestResource.class)` (Testcontainers ActiveMQ Artemis); end-to-end test: publish order message → verify `OrderService.save()` called → verify `CatalogService.updateInventoryItems()` called → verify inventory alert logged when quantity below 50
- [ ] 6.8 **Documentation**: Document JMS-to-AMQP migration; document broker topology on OpenShift; create runbook for dead-letter queue monitoring

---

## Task Group 7: REST Layer Migration (`com.redhat.coolstore.rest`)

> Migrate JAX-RS endpoints to Quarkus RESTEasy Reactive; wire Keycloak OIDC (rgctl step 70 — highest harmonic, highest risk).

- [ ] 7.1 Migrate `RestApplication.java`: replace `extends Application` with Quarkus RESTEasy Reactive auto-registration (delete class if empty, or retain with `@ApplicationPath("/api")` for explicit path)
- [ ] 7.2 Migrate `ProductEndpoint.java`: update imports `javax.ws.rs.*` → `jakarta.ws.rs.*`; change scope from `@RequestScoped` to `@ApplicationScoped` (RESTEasy Reactive default); retain `@Path("/products")`, `@GET`, `@Produces(MediaType.APPLICATION_JSON)` annotations unchanged
- [ ] 7.3 Migrate `OrderEndpoint.java`: same import namespace update; change to `@ApplicationScoped`; retain REST annotations
- [ ] 7.4 Migrate `CartEndpoint.java` (most complex REST endpoint):
  - Update imports `javax.ws.rs.*` → `jakarta.ws.rs.*`, `javax.enterprise.context.SessionScoped` → `jakarta.enterprise.context.SessionScoped`
  - Inject `ShoppingCartService` (now `@SessionScoped` CDI bean)
  - Verify all 6 operations (GET cart, POST checkout, POST add, POST set, DELETE item, `dedupeCartItems`) produce identical responses to characterisation tests
- [ ] 7.5 Wire Keycloak OIDC security: add `@RolesAllowed` / `@Authenticated` annotations to endpoints per original Keycloak role mapping from `realm-export.json`; configure `quarkus.oidc.auth-server-url`, `quarkus.oidc.client-id` from OpenShift Secret
- [ ] 7.6 Remediate the **1 taint flow** identified by rgctl: review input path → data flow → output path; apply input validation (Bean Validation `@Valid` / `@NotNull` / `@Min`) where user data enters REST endpoints
- [ ] 7.7 Validate all 12 REST operations against characterisation test suite (Task 1.8 golden baseline); all tests must pass green
- [ ] 7.8 **Test Generation**: Use mig-test-gen for REST layer
  - Expected: `@QuarkusTest` with REST-assured; test all 6 `CartEndpoint` operations; test `ProductEndpoint` list and get-by-id; test `OrderEndpoint` list and get-by-id; include negative cases (404, 400 validation errors); include Keycloak OIDC token-gated endpoint tests using `quarkus-test-security`
- [ ] 7.9 **Deployment**: Use mig-deploy for REST service
  - Configure `Route` with TLS termination (edge) for HTTPS
  - Expose port 8080 internally; expose via OpenShift Route
  - Connect to PostgreSQL service, AMQ Broker service, Keycloak service
- [ ] 7.10 **Documentation**: Update API documentation (OpenAPI spec via `quarkus-smallrye-openapi`); document OIDC security configuration; update README with new endpoint base URL

---

## Task Group 8: Containerisation

> Build optimised container image; integrate with OpenShift image pipeline.

- [ ] 8.1 Write `Containerfile.jvm` using Quarkus fast-jar layout:
  ```
  FROM registry.access.redhat.com/ubi9/openjdk-17-runtime:latest
  WORKDIR /deployments
  COPY --chown=185 target/quarkus-app/lib/ ./lib/
  COPY --chown=185 target/quarkus-app/*.jar ./
  COPY --chown=185 target/quarkus-app/app/ ./app/
  COPY --chown=185 target/quarkus-app/quarkus/ ./quarkus/
  EXPOSE 8080 8443
  USER 185
  ENTRYPOINT ["java", "-jar", "quarkus-run.jar"]
  ```
- [ ] 8.2 (Optional) Write `Containerfile.native` for GraalVM native image build (multi-stage with `quarkus-native-builder` image)
- [ ] 8.3 Integrate image build into OpenShift Pipeline: `buildah build -f Containerfile.jvm -t quay.io/coolstore/coolstore-quarkus:$(git rev-parse --short HEAD) .`
- [ ] 8.4 Add image scan step to pipeline using **Red Hat ACS** (`roxctl image check`) or **Trivy**; fail pipeline on critical/high CVEs
- [ ] 8.5 Push image to Quay.io or OpenShift internal registry; tag with commit SHA and `latest`
- [ ] 8.6 Configure `PersistentVolumeClaim` for audit log directory (`./device-inventory-audit-logs/`) or confirm emptyDir is acceptable (logs survive pod restart only on PVC)
- [ ] 8.7 **Test Generation**: Use mig-test-gen for container validation
  - Expected: Container smoke tests: `podman run --rm ... /bin/sh -c 'curl -s http://localhost:8080/q/health/ready | grep UP'`; image SBOM generation and validation
- [ ] 8.8 **Documentation**: Document image build process; document image tagging strategy; add `README.md` section on running locally with Podman

---

## Task Group 9: OpenShift Deployment & Infrastructure

> Deploy all components to OpenShift; configure secrets, networking, and autoscaling.

- [ ] 9.1 Create OpenShift **Namespace** (`coolstore-dev`, `coolstore-staging`, `coolstore-prod`)
- [ ] 9.2 Deploy **PostgreSQL** via CrunchyData PGO operator or OpenShift PostgreSQL template; create `coolstoredb` database; configure `Secret` with credentials
- [ ] 9.3 Deploy **AMQ Broker** via AMQ Broker Operator: `ActiveMQArtemis` CR with `topic/orders` address configuration; configure `Secret` with AMQP credentials
- [ ] 9.4 Deploy **Keycloak / RHSSO** via Keycloak Operator; import `realm-export.json`; configure `Secret` with OIDC client secret
- [ ] 9.5 Create Kubernetes **`ConfigMap`** for non-sensitive config: `quarkus.flyway.migrate-at-start=true`, `quarkus.log.level=INFO`, `quarkus.http.port=8080`, audit log directory path
- [ ] 9.6 Create Kubernetes **`Secret`** for sensitive config: DB credentials, AMQP credentials, OIDC client secret, any rotated secrets from Task 1.1
- [ ] 9.7 Write **`Deployment`** manifest: image reference, `envFrom` for ConfigMap and Secret, `livenessProbe` → `/q/health/live`, `readinessProbe` → `/q/health/ready`, resource `requests`/`limits`, `PVC` volume mount for audit logs
- [ ] 9.8 Write **`Service`** manifest: ClusterIP on port 8080
- [ ] 9.9 Write **`Route`** manifest: TLS edge termination, host `coolstore.apps.<cluster-domain>`
- [ ] 9.10 Write **`HorizontalPodAutoscaler`** manifest: min 2 replicas, max 10, CPU target 70%
- [ ] 9.11 Configure **OpenShift Pipelines** `Pipeline` and `PipelineRun` YAML; store in `deploy/pipelines/`
- [ ] 9.12 Set up **GitOps with ArgoCD** (OpenShift GitOps): `Application` CR pointing to `deploy/` directory on `main` branch; sync policy `automated` with `selfHeal: true`
- [ ] 9.13 **Test Generation**: Use mig-test-gen for deployment validation
  - Expected: Deployment smoke tests in Tekton pipeline — `oc rollout status deployment/coolstore-quarkus`; `/q/health/ready` returns 200; run REST-assured characterisation suite against deployed pod
- [ ] 9.14 **Documentation**: Create operator runbook (scale up/down, restart, log access); document ArgoCD sync procedure; document rollback procedure (revert Git commit → ArgoCD syncs previous image tag)

---

## Task Group 10: Parallel Run, Validation & Cutover

> Run monolith and Quarkus in parallel; validate; switch traffic; decommission monolith.

- [ ] 10.1 Deploy Quarkus pod in `coolstore-dev` namespace; keep monolith WAR deployment active on JBoss EAP in parallel
- [ ] 10.2 Run full characterisation test suite (Task 1.8) against **both** monolith and Quarkus; diff responses; resolve all divergences
- [ ] 10.3 Run **performance baseline comparison**: Gatling / k6 load test against monolith (record p50, p95, p99 latency, throughput, error rate); run identical test against Quarkus; ensure Quarkus meets or exceeds baseline
- [ ] 10.4 Execute **OWASP ZAP security scan** against Quarkus REST endpoints; validate taint flow remediation (Task 7.6); ensure 0 critical findings
- [ ] 10.5 Conduct **48-hour parallel run** in staging: shadow-mode traffic routing; compare results of both systems against the same requests
- [ ] 10.6 Obtain sign-off from stakeholders on test results, security scan, and performance comparison
- [ ] 10.7 **Canary cutover**: route 10% of production traffic to Quarkus pod using OpenShift Service Mesh weight-based routing; monitor error rate and latency for 24 hours; escalate to 50% → 100%
- [ ] 10.8 Decommission monolith WAR deployment after 30-day stability window; remove JBoss EAP infrastructure
- [ ] 10.9 Remove `system`-scoped audit library JAR from repository once `AuditService` replacement is confirmed in production
- [ ] 10.10 **Documentation**: Write post-migration report; document performance comparison results; archive monolith codebase with deprecation notice; update team runbooks
