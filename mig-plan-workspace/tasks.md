# Migration Tasks: coolstore Java EE 7 / EAP 7.4 → Quarkus 3.15 LTS / Java 21 / OpenShift

Source of truth: `mig-prompt-workspace/migration-prompt.md` (design decisions 1–14). Task order follows the rgctl `migration_plan.json` app order (model → utils → service → rest → webapp → weblogic → persistence). Strategy: big bang, in place, single module.
Environment note: JDK 21 and Maven 3.9 are installed into `/tmp` for verification. podman and oc are unavailable, so containers and manifests are validated statically.

## Task Group 1: Build Foundation (Quarkus 3.15 BOM, Java 21, audit lib 2.0.0 install)

- [x] 1.1 Rewrite `pom.xml`: `jar` packaging, `io.quarkus.platform:quarkus-bom:3.15.x`, `maven.compiler.release=21`, quarkus-maven-plugin, surefire/failsafe 3.x; remove `maven.test.skip`, `finalName ROOT`, war plugin
- [x] 1.2 Replace Java EE deps (javaee-api/web-api 7, jboss-jms-api, jboss-rmi-api, flyway-core 4.1.2) with quarkus-rest, quarkus-rest-jackson, quarkus-arc, quarkus-hibernate-orm, quarkus-jdbc-postgresql, quarkus-flyway, quarkus-messaging, quarkus-oidc, quarkus-smallrye-health, org.eclipse.parsson (JSON-P); test: quarkus-junit5, rest-assured, quarkus-jdbc-h2, quarkus-jacoco, mockito
- [x] 1.3 Audit lib (rule audit-logging-0001): bind `maven-install-plugin:install-file` to `validate` for `lib/audit-logging-library-2.0.0.jar` (com.enterprise:audit-logging-library:2.0.0, pomFile from jar metadata or generatePom); depend on it at compile scope; delete `lib/audit-logging-library-1.0.0.jar`
- [x] 1.4 Create `src/main/resources/application.properties` skeleton (prod/dev/test profiles); delete `src/main/webapp/WEB-INF/web.xml` (`<distributable/>`, Konveyor session-00000) and `beans.xml`
- [x] 1.5 Global `javax.*` → `jakarta.*` rename in `src/main/java`
- [x] 1.6 **Test Generation**: `/skill mig-test-gen` for build sanity. Expected: an empty `@QuarkusTest` context-boot test; `mvn -B verify` runs on JDK 21
- [x] 1.7 **Containerization**: `/skill mig-containerize` input: Quarkus fast-jar output `target/quarkus-app/`. Config needs: none yet
- [x] 1.8 **Deployment**: `/skill mig-deploy` input: artifact type = JVM fast-jar, port 8080
- [x] 1.9 **Documentation**: README "Build" section (JDK 21, Maven, audit-lib install step)

## Task Group 2: Persistence, Datasource & Flyway (model + persistence + utils.DataBaseMigrationStartup)

- [x] 2.1 Remove `META-INF/persistence.xml` and `persistence/Resources.java`; inject `EntityManager` directly in CatalogService and OrderService
- [x] 2.2 Configure `quarkus.datasource.*` from env (`DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_USERNAME`, `DB_PASSWORD`), `quarkus.hibernate-orm.database.generation=none`; test profile uses H2 (`MODE=PostgreSQL`)
- [x] 2.3 Entities `CatalogItemEntity`, `InventoryEntity`, `Order`, `OrderItem`: drop `@XmlRootElement`; pin `@GeneratedValue` to a sequence matching V1_1 (`hibernate_sequence`, allocationSize 1); verify Hibernate 6 mapping of `ORDERS`/`ORDER_ITEMS`/`PRODUCT_CATALOG`/`INVENTORY`
- [x] 2.4 Delete `utils/DataBaseMigrationStartup.java`; set `quarkus.flyway.migrate-at-start=true`, `baseline-on-migrate=true`; keep `db/migration/V1_1__CreateSchema.sql`, `V1_2__AddInitialData.sql` (check H2 compatibility)
- [x] 2.5 **Test Generation**: `/skill mig-test-gen` for persistence. Expected: `@QuarkusTest` that Flyway seeds the catalog (product count, item `329299` exists with inventory), order persist/find round trip
- [x] 2.6 **Containerization**: `/skill mig-containerize` config needs: DB_* env vars, DB_PASSWORD from a Secret
- [x] 2.7 **Deployment**: `/skill mig-deploy` dependencies: PostgreSQL 15 (`rhel9/postgresql-15`, PVC), Service `coolstore-db:5432`
- [x] 2.8 **Documentation**: datasource env var table and Flyway behaviour (baseline on an existing DB)

## Task Group 3: Service Layer EJB → CDI, Local Shipping, Stateless Cart (service)

- [x] 3.1 `CatalogService`, `ProductService`, `OrderService`: `@Stateless` → `@ApplicationScoped`, `@Transactional` on writes (`save`, `updateInventoryItems`)
- [x] 3.2 `ShippingService`: drop `@Stateless @Remote`, make it `@ApplicationScoped`; turn `ShippingServiceRemote` into a plain local interface (or delete it); remove the JNDI `lookupShippingServiceRemote()` and inject the bean into `ShoppingCartService`
- [x] 3.3 `ShoppingCartService`: `@Stateful` → `@ApplicationScoped` with `ConcurrentHashMap<String, ShoppingCart>` keyed by `cartId` (`computeIfAbsent`), behind a small `CartStore` abstraction for a future Data Grid/Redis implementation; keep the pricing logic unchanged
- [x] 3.4 `PromoService` (already `@ApplicationScoped`) and `model.ShoppingCart` (drop `@Dependent`; it is a POJO): review only; replace `System.out` with the logger; keep the `utils/Producers` JUL Logger producer or switch to JBoss Logging
- [x] 3.5 **Test Generation**: `/skill mig-test-gen` characterization unit tests. Expected: shipping tiers 2.99/4.99/6.99/8.99/10.99, insurance 2%/1.5%/1% HALF_UP, promo 25% off `329299`, free shipping at ≥ 75, carts isolated per cartId
- [x] 3.6 **Containerization**: `/skill mig-containerize`. Note: the in-memory cart store keeps the image stateless apart from the heap; no volume needed
- [x] 3.7 **Deployment**: `/skill mig-deploy`. replicas=1 (or Route sticky cookie) until an external CartStore exists; document the HPA caveat
- [x] 3.8 **Documentation**: ADR "cart state without HTTP sessions" (Konveyor session-00000 resolution)

## Task Group 4: Messaging JMS topic/orders → SmallRye Reactive Messaging (in-process)

- [x] 4.1 `ShoppingCartOrderProcessor`: replace `JMSContext` + `@Resource Topic` with `@Channel("orders") Emitter<String>`; payload `Transformers.shoppingCartToJson(cart)` unchanged
- [x] 4.2 `OrderServiceMDB` → `@ApplicationScoped` bean with `@Incoming("orders-persist") @Blocking @Transactional` that saves the Order and decrements inventory
- [x] 4.3 `InventoryNotificationMDB` → `@Incoming("orders-inventory")` low-stock (< 50) warning via logger; remove the WebLogic t3 JNDI, `PortableRemoteObject`, TopicConnection code
- [x] 4.4 Fan-out: emitter → `@Broadcast` (or two outgoing channels) so both consumers receive every order; config-only path documented for `quarkus-messaging-amqp`/`kafka`
- [x] 4.5 **Test Generation**: `/skill mig-test-gen` messaging tests. Expected: checkout → order row persisted + inventory decremented (Awaitility), `Transformers` JSON round trip (cart → JSON → Order) with the keys productSku/quantity/orderValue/…
- [x] 4.6 **Containerization**: `/skill mig-containerize`. No broker in the image; optional `MP_MESSAGING_*` env overrides
- [x] 4.7 **Deployment**: `/skill mig-deploy`. No broker resources for now; note the AMQ Broker/AMQ Streams follow-up
- [x] 4.8 **Documentation**: ADR "in-memory messaging vs AMQP/Kafka" (trade-off: no durability, same as the single-node EAP setup)

## Task Group 5: Audit Logging 2.0.0 (rules audit-logging-0001..0005)

- [x] 5.1 `OrderService`: replace `FileSystemAuditLogger` and the `./device-inventory-audit-logs` config with `StreamableAuditLogger(AuditConfiguration)` using `setStreamProtocol("TCP")`, `setStreamHost(${audit.stream.host})`, `setStreamPort(${audit.stream.port})` (env `AUDIT_STREAM_HOST`/`AUDIT_STREAM_PORT`, default localhost:5170)
- [x] 5.2 Produce the logger through a CDI `@ApplicationScoped` producer with lazy, failure-tolerant init and `@PreDestroy close()`; the app must start without a collector
- [x] 5.3 Emit `new AuditEvent(Instant.now(), "ORDER_CREATED", userId, null, "coolstore", "OrderService", "CREATE_ORDER", "order/{id}", AuditResult.SUCCESS|FAILURE, msg, details, correlationId, null, null)` via `logEventAsync(event)` with `.exceptionally(log)`; no `builder()`, `logEvent`, `logSuccess`, `logFailure`
- [x] 5.4 Check that Jackson (jsr310) from the Quarkus BOM satisfies the library (it compiled against 2.13.5)
- [x] 5.5 **Test Generation**: `/skill mig-test-gen` audit tests. Expected: embedded `ServerSocket` receives a JSON event with `event_type=ORDER_CREATED`; with no server, order save still succeeds; grep check that no legacy API calls remain
- [x] 5.6 **Containerization**: `/skill mig-containerize` config needs: AUDIT_STREAM_HOST/PORT (ConfigMap)
- [x] 5.7 **Deployment**: `/skill mig-deploy` network: egress TCP to the audit collector Service (NetworkPolicy if enforced)
- [x] 5.8 **Documentation**: audit 1.x → 2.0 notes (record ctor field order, TCP config, async semantics)

## Task Group 6: REST Layer & OIDC Security (rest)

- [x] 6.1 Keep `RestApplication @ApplicationPath("/services")` (or set `quarkus.rest.path=/services`); `ProductEndpoint`, `OrderEndpoint` stay `@RequestScoped`/default; remove the `Serializable` boilerplate
- [x] 6.2 `CartEndpoint`: drop `@SessionScoped`, make it `@ApplicationScoped`; keep all 5 paths, verbs and `ShoppingCart` JSON exactly
- [x] 6.3 Make Jackson output match the old EAP (RESTEasy Jackson) JSON: getter-based field names (`desc`, `itemList`, `shoppingCartItemList`), include nulls, numbers as doubles
- [x] 6.4 Add `quarkus-oidc` (`application-type=service`, `auth-server-url=${OIDC_AUTH_SERVER_URL}/realms/eap`, `client-id=eap-app`, `tenant-enabled=${OIDC_ENABLED:false}`); HTTP permission policy `permit` on `/services/*` (anonymous access stays as today)
- [x] 6.5 **Test Generation**: `/skill mig-test-gen` REST contract tests (REST Assured). Expected: every endpoint returns 200 with exactly the field names, cart add/merge(tmpId)/delete/checkout flow, 404/empty behaviour for unknown ids, OIDC disabled in the test profile
- [x] 6.6 **Containerization**: `/skill mig-containerize` config needs: OIDC_ENABLED, OIDC_AUTH_SERVER_URL (ConfigMap); no client secret (public client)
- [x] 6.7 **Deployment**: `/skill mig-deploy`. Route edge TLS on `/`; optional dependency on RH build of Keycloak (`realm-export.json` import)
- [x] 6.8 **Documentation**: API reference table and the auth/SSO configuration guide

## Task Group 7: UI Static Resources & WebLogic/EAP Cleanup (webapp, weblogic.*)

- [x] 7.1 Move `src/main/webapp/{app,partials,bower_components,coolstore.json,keycloak.json}` to `src/main/resources/META-INF/resources/`
- [x] 7.2 Convert `index.jsp` to static `index.html` (strip scriptlets); replace `health.jsp` with SmallRye Health (`/q/health`); externalize `keycloak.json` `auth-server-url`
- [x] 7.3 Delete `src/main/java/weblogic/**` (ApplicationLifecycleEvent, ApplicationLifecycleListener, NonCatalogLogger); `utils/StartupListener` → `@Observes StartupEvent/ShutdownEvent`
- [x] 7.4 Final grep gate: no `javax.`, `javax.ejb`, `javax.jms`, `InitialContext`, `weblogic`, `FileSystemAuditLogger`, `<scope>system` left
- [x] 7.5 **Test Generation**: `/skill mig-test-gen` static resource tests. Expected: `GET /` → 200 HTML containing `ng-app`, `/app/app.js` and `/coolstore.json` served, `/q/health/ready` UP
- [x] 7.6 **Containerization**: `/skill mig-containerize`. Static assets live inside the jar; `.containerignore` excludes `node_modules`/`target`
- [x] 7.7 **Deployment**: `/skill mig-deploy`. Probes on `/q/health/live` and `/q/health/ready`
- [x] 7.8 **Documentation**: list of removed WebLogic/EAP artifacts with their replacements

## Task Group 8: Test Suite Consolidation & Quality Gates (mig-test-gen)

- [x] 8.1 Characterization test suite (pricing, Transformers JSON, REST contract) green on the migrated code
- [x] 8.2 Integration tests `@QuarkusTest` on H2 + Flyway; messaging and audit tests
- [ ] 8.3 (DEFERRED: quarkus-jacoco not added) JaCoCo report; ≥ 70% line coverage on `com.redhat.coolstore`
- [x] 8.4 E2E smoke script `src/test/e2e/smoke.sh` (curl browse → add → checkout → orders) usable against a local jar or the Route
- [ ] 8.5 (DEFERRED: not measured) Performance baseline: record startup time and RSS in the README; optional k6 script for `/services/products`
- [x] 8.6 **Test Generation**: `/skill mig-test-gen` full run. Expected: `mvn verify` green on JDK 21 (in /tmp)
- [x] 8.7 **Containerization**: `/skill mig-containerize`. Run the smoke test against the container when podman is available
- [x] 8.8 **Deployment**: `/skill mig-deploy`. Post-deploy smoke step referenced in the deployment runbook
- [x] 8.9 **Documentation**: testing guide (how to run unit/integration/e2e)

## Task Group 9: Containerization (mig-containerize)

- [x] 9.1 Multi-stage `Containerfile`: builder `registry.access.redhat.com/ubi9/openjdk-21` (mvn package, incl. audit-lib install), runtime `ubi9/openjdk-21-runtime`, copy `target/quarkus-app/`, `USER 185`, `EXPOSE 8080`
- [x] 9.2 `.containerignore`; OCI labels; `JAVA_OPTS_APPEND` and `-Dquarkus.http.host=0.0.0.0`
- [x] 9.3 Check: no secrets baked in, all config via env; image scanning step (Trivy/ACS) documented for CI
- [x] 9.4 Optional `compose.yaml` (app + postgresql-15) for local runs
- [x] 9.5 **Test Generation**: `/skill mig-test-gen` container smoke test definition (health + products)
- [x] 9.6 **Containerization**: `/skill mig-containerize` for coolstore (single image). Config needs: DB_*, AUDIT_*, OIDC_*
- [x] 9.7 **Deployment**: `/skill mig-deploy` input: image `image-registry.openshift-image-registry.svc:5000/coolstore/coolstore:latest` (ImageStream)
- [x] 9.8 **Documentation**: build/run container instructions

## Task Group 10: OpenShift Deployment Manifests & Runbook (mig-deploy)

- [x] 10.1 `deploy/openshift/base` (Kustomize): Deployment (RollingUpdate, requests 250m/256Mi, limits 1/512Mi, startup/liveness/readiness probes), Service 8080, Route edge TLS, ConfigMap, Secret template, ImageStream + BuildConfig (Docker strategy)
- [x] 10.2 PostgreSQL: `rhel9/postgresql-15` Deployment + PVC + Service + Secret
- [x] 10.3 HPA (CPU 70%, max 1 until an external CartStore exists, documented) and an optional NetworkPolicy (app→db, app→audit collector)
- [x] 10.4 Overlays `dev`/`prod`; offline validation (YAML parse, kubeconform if fetchable)
- [x] 10.5 Runbook: deploy (`oc apply -k`), rollback (`oc rollout undo` and keeping the EAP deployment until cutover), monitoring
- [x] 10.6 **Test Generation**: `/skill mig-test-gen` post-deploy smoke (Route URL) and manifest lint test
- [x] 10.7 **Containerization**: `/skill mig-containerize` image reference and pull policy consistency check
- [x] 10.8 **Deployment**: `/skill mig-deploy` for coolstore + postgresql. Network: Route → svc:8080, app → coolstore-db:5432, app → audit:5170
- [x] 10.9 **Documentation**: README "Deploy to OpenShift" plus a team enablement note (Quarkus dev mode, config profiles)
