# Migration Tasks

coolstore (APP_ID=7) — Java EE 7 / JBoss EAP 7.4 monolith → Quarkus 3.x on Java 21,
deployed to Red Hat OpenShift. Task groups follow the rgctl migration order
(webapp scaffold → legacy shims → model → persistence → REST → utils → services)
wrapped in the phased approach from `mig-prompt-workspace/migration-prompt.md`.

## Task Group 1: Foundation & Baseline

- [ ] 1.1 Set up local dev environment: JDK 21, Podman, Maven 3.9+, `oc` CLI; record versions in README
- [ ] 1.2 Capture baseline behavior of the current EAP deployment: REST endpoint contracts (`/rest/products`, `/rest/cart`, `/rest/orders`), cart pricing rules (PromoService, ShippingService), checkout flow, JMS order processing logs
- [ ] 1.3 Write characterization tests for pricing/shipping/promotion logic (ShoppingCartService, PromoService, ShippingService, ShoppingCartOrderProcessor) that run against the current code
- [ ] 1.4 Write characterization tests for the three JAX-RS endpoints (CartEndpoint, OrderEndpoint, ProductEndpoint) capturing request/response shapes
- [ ] 1.5 Remediate the 2 potential secrets flagged by rgctl security analysis and document the 1 vulnerable taint flow for fixing in Task Group 2
- [ ] 1.6 Set up CI pipeline placeholder (OpenShift Pipelines/Tekton or GitHub Actions) that runs the characterization suite on every commit
- [ ] 1.7 **Test Generation**: Use mig-test-gen to create the characterization baseline
  - Command: `/skill mig-test-gen` targeting service + rest packages
  - Expected: characterization tests for services and REST contracts; JMS order flow test
- [ ] 1.8 **Documentation**: Document baseline metrics (build time, startup time, endpoint latency) for later parity comparison

## Task Group 2: Quarkus Runtime Migration (Core)

- [ ] 2.1 Restructure `pom.xml`: WAR → Quarkus JAR, add `quarkus-bom`, quarkus-maven-plugin, set Java 21, remove `javaee-web-api`/`javaee-api` provided scopes
- [ ] 2.2 Apply `javax.*` → `jakarta.*` namespace migration across all 30 Java files (CDI, JAX-RS, JPA, JMS annotations)
- [ ] 2.3 Migrate CDI wiring: verify `beans.xml` → Quarkus ArC, migrate `Producers`, `Resources`, `StartupListener` to Quarkus lifecycle (`@Observes StartupEvent`)
- [ ] 2.4 Migrate persistence: `persistence.xml` → Quarkus Hibernate ORM config, keep PostgreSQL + JTA datasource; verify JPA entities (`CatalogItemEntity`, `InventoryEntity`, `Order`, `OrderItem`, `Product`, `Promotion`, `ShoppingCart`, `ShoppingCartItem`) map correctly
- [ ] 2.5 Migrate Flyway: replace `DataBaseMigrationStartup` with `quarkus-flyway` extension; verify V1_1/V1_2 migrations apply on a fresh PostgreSQL instance
- [ ] 2.6 Migrate REST layer: JAX-RS endpoints (`CartEndpoint`, `OrderEndpoint`, `ProductEndpoint`, `RestApplication`) → quarkus-rest, preserving the existing REST contract consumed by the AngularJS frontend
- [ ] 2.7 Migrate service layer: EJB `@Stateless`/`@Singleton` services → CDI beans with `@Transactional` (CatalogService, ProductService, PromoService, ShippingService, ShoppingCartService, ShoppingCartOrderProcessor, OrderService)
- [ ] 2.8 Fix the flagged taint flow and refactor the most circular dependencies (9 detected) opportunistically during migration
- [ ] 2.9 **Test Generation**: Use mig-test-gen for migrated services and persistence
  - Command: `/skill mig-test-gen` targeting model/persistence/rest/service packages
  - Expected: unit tests per service, `@QuarkusTest` integration tests for REST + JPA + Flyway
- [ ] 2.10 **Documentation**: Document Quarkus configuration (`application.properties`), dependency changes, and javax→jakarta migration notes

## Task Group 3: Legacy Coupling Removal

- [ ] 3.1 Remove WebLogic compatibility shims (`src/main/java/weblogic/**`: ApplicationLifecycleEvent, ApplicationLifecycleListener, weblogic.i18n.logging.NonCatalogLogger) and all references (StartupListener usage)
- [ ] 3.2 Replace remote EJB `ShippingServiceRemote` with an in-process CDI service or REST boundary; delete RMI/jboss-rmi dependency
- [ ] 3.3 Upgrade `audit-logging-library` 1.0.0 → 2.0.0: replace system-scoped `lib/audit-logging-library-1.0.0.jar` with a proper Maven dependency (v2.0.0 jar is already in `lib/`); adapt call sites to the 2.x API (mandatory ruleset `audit-logging-0001`, requires Java 21)
- [ ] 3.4 Remove JBoss-specific module/datasource assumptions (JNDI `java:jboss/datasources/CoolstoreDS`) in favor of Quarkus datasource configuration
- [ ] 3.5 **Test Generation**: Use mig-test-gen for audit-logging integration
  - Command: `/skill mig-test-gen` targeting audit logging call sites
  - Expected: tests proving v2.x logging emits expected audit records
- [ ] 3.6 **Documentation**: Record removed legacy APIs and their replacements in the migration guide

## Task Group 4: Messaging & Security Migration

- [ ] 4.1 Migrate JMS MDBs (`OrderServiceMDB`, `InventoryNotificationMDB`) to Quarkus messaging consumers on `topic/orders` (Artemis/AMQP); preserve message payload format (JSON order objects)
- [ ] 4.2 Validate clustered/dual-instance message processing parity (both instances processing orders as on EAP HA today)
- [ ] 4.3 Migrate Keycloak OIDC: `web.xml` security constraints → `quarkus-oidc` extension; reuse the existing `eap` realm (`realm-export.json`); protect REST endpoints
- [ ] 4.4 Migrate web session/cart handling for the SPA (cookie/token-based) to work behind `quarkus-oidc`
- [ ] 4.5 **Test Generation**: Use mig-test-gen for messaging and security
  - Command: `/skill mig-test-gen` targeting JMS consumers and OIDC-protected endpoints
  - Expected: integration tests with embedded Artemis + Keycloak testcontainers; auth rejection/acceptance tests
- [ ] 4.6 **Documentation**: Document JMS topology, Keycloak configuration, and HA behavior on Quarkus

## Task Group 5: Frontend & Static Assets

- [ ] 5.1 Serve the AngularJS app (`src/main/webapp/app`, `partials`, `css`, `imgs`) as Quarkus static resources (`META-INF/resources`) with identical paths
- [ ] 5.2 Pin bower-managed vendored libraries (`bower_components/**`) to CDN URLs or npm-managed copies; remove the vendored tree from the repo if replaced
- [ ] 5.3 Verify SPA routing, login/logout (`logout`/`login` are top graph hotspots), add-to-cart and checkout flows against the migrated REST API
- [ ] 5.4 **Test Generation**: Use mig-test-gen for frontend-critical API paths
  - Command: `/skill mig-test-gen` targeting cart/checkout REST flows used by the SPA
  - Expected: E2E test script for login → browse → add-to-cart → checkout
- [ ] 5.5 **Documentation**: Update README for the new frontend asset strategy

## Task Group 6: Containerization & OpenShift Deployment

- [ ] 6.1 Create multi-stage Containerfile (Podman-compatible): Maven build stage (JDK 21) → Quarkus fast-jar runtime image
- [ ] 6.2 Scan images for vulnerabilities; pin base images; configure OpenShift internal registry push
- [ ] 6.3 Author OpenShift manifests: Deployment (2 replicas), Service, Route, readiness/liveness probes (`/q/health`), resource limits, HPA
- [ ] 6.4 Externalize configuration: ConfigMap (QUARKUS_DATASOURCE_URL, Keycloak auth URL, Artemis broker settings) and Secrets (DB credentials, OIDC client secret)
- [ ] 6.5 Provision/bind PostgreSQL and Keycloak on OpenShift (operators or templates); import the `eap` realm
- [ ] 6.6 Fill the `<!-- TODO: Add OpenShift profile here -->` pom placeholder or remove it in favor of the Containerfile pipeline
- [ ] 6.7 **Containerization**: Use mig-containerization for the coolstore image
  - Command: `/skill mig-containerize` for coolstore monolith
  - Config needs: QUARKUS_DATASOURCE_* (Secret), QUARKUS_OIDC_* (Secret/ConfigMap), Artemis URL (ConfigMap)
- [ ] 6.8 **Deployment**: Use mig-deploy to create deployment manifests
  - Command: `/skill mig-deploy` for coolstore
  - Dependencies: PostgreSQL service, Keycloak service, Artemis broker
  - Network: expose 8080 via Route; connect to postgres:5432, keycloak:8080, artemis:61616
- [ ] 6.9 **Test Generation**: Use mig-test-gen for deployment smoke tests
  - Command: `/skill mig-test-gen` targeting health endpoints and smoke journeys
  - Expected: smoke test hitting `/q/health` and the checkout E2E flow on-cluster
- [ ] 6.10 **Documentation**: Runbook for build → registry → deploy; rollback procedure

## Task Group 7: Cutover, Validation & Training

- [ ] 7.1 Run full test suites (characterization, unit, integration, E2E) against the OpenShift deployment; compare against EAP baseline metrics from Task 1.8
- [ ] 7.2 Load/performance test: validate startup time, throughput, and latency parity or improvement
- [ ] 7.3 Execute cutover plan (traffic switch to OpenShift Route), keep EAP rollback path warm for one sprint
- [ ] 7.4 Decommission EAP hosts and JBoss CLI-datasource infrastructure after a stable observation window
- [ ] 7.5 Train the team on Quarkus development, Quarkus config, and OpenShift operations; capture FAQ
- [ ] 7.6 **Documentation**: Final migration report: what moved where, open risks, monitoring/alerting setup, README overhaul
