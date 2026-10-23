# Migration Report

**Project**: coolstore monolith (`com.redhat.coolstore:monolith`)
**Migration**: Java EE 7 / JBoss EAP 7.4 (Java 8, WAR) → Quarkus 3.15.1 LTS on Java 21, Red Hat UBI container, Red Hat OpenShift
**Date**: 2026-10-23
**Duration**: about 15 minutes of wall-clock time for the whole orchestration (14:18 to about 14:32 UTC)
**Branch**: `konveyor/migration-1792765075`

---

## Executive Summary

The CoolStore monolith was a Java EE 7 application for JBoss EAP 7.4. It used EJBs (stateless, singleton-startup, message-driven), a JMS topic, a remote EJB interface, a JTA datasource configured on the server, Flyway 4, WebLogic API stubs, and a `system`-scoped audit-logging library at 1.0.0. The run moved it in place to a single Quarkus 3.15.1 application on Java 21. The REST paths and JSON contracts used by the AngularJS front end are unchanged, and the front end is now served as static resources.

The run parameters did not name a target, so one was **inferred and treated as an assumption**. The custom Konveyor rules (`rules/rule.yaml`) are labelled `konveyor.io/target=quarkus3` and ask for Java 21 plus the audit library 2.x. The remote branches `quarkus` and `kai/migrate-to-quarkus` point the same way. Deployment to OpenShift follows the MigIQ default. The mandatory finding in `.konveyor/analysis.json` (`session-00000`, `<distributable/>` HTTP session clustering) and all five audit-logging rules are resolved.

The build was checked independently after execution. `mvn -B package` on Temurin 21 with Maven 3.9.9 reports **BUILD SUCCESS, 13 of 13 tests passed**. This includes `@QuarkusTest` REST contract tests that run Flyway migrations on H2 and check the full checkout flow end to end. Container and OpenShift files were produced but only checked statically, because podman and oc are not available in this environment.

**Migration Objective**: Modernize the EAP monolith into a cloud-native Quarkus application that can run on OpenShift, while keeping its behavior and API contract.

**Approach**: A single "big bang" conversion of the one-module app (30 Java files).

**Overall Status**: ⚠️ **Partial Success**. All code-migration and packaging work is done and the build and tests pass. 2 non-blocking subtasks were deferred, and the container and deployment have not yet been run on a real cluster.

**Key Achievements**:
- All Java EE APIs replaced. No `javax.ejb`, `javax.jms`, `javax.ws.rs`, `javax.persistence` or `javax.inject` imports remain in `src/main`; the code uses Jakarta EE 10, CDI and Quarkus extensions.
- JMS topic and MDBs replaced with SmallRye Reactive Messaging on an in-memory channel (`orders`). The remote EJB became a local CDI bean. The WebLogic stubs are deleted.
- Audit library upgraded to 2.0.0 and installed from `lib/` instead of `system` scope. The code uses the `AuditEvent` record constructor, `StreamableAuditLogger` over TCP and `logEventAsync`, and keeps running when no collector is reachable.
- `<distributable/>` and `web.xml` removed. The cart now lives in a `CartStore` keyed by `cartId`, which also fixes an existing bug where `cartId` was ignored.
- Externalized config: PostgreSQL settings come from `DB_*` env vars, Flyway runs at start, OIDC (Keycloak `eap` realm) is switched on with `OIDC_ENABLED`, and SmallRye Health probes are available.
- A UBI9 OpenJDK 21 multi-stage `Containerfile`, Kustomize OpenShift manifests (app plus PostgreSQL 15, dev and prod overlays), a GitHub Actions build on Java 21, and an updated README.

**Challenges Encountered**:
- No JDK, Maven, podman, oc, unzip or PyYAML was installed. A JDK and Maven were downloaded into `/tmp`. The audit jar API was read with Python `zipfile`, and the YAML was checked with SnakeYAML.
- About 90% of the rgctl graph comes from vendored `bower_components` JavaScript, which hides the Java signal in the hotspot and community metrics. Java-only filtering had to be done by hand.
- Moving off the `system` scope means a clean Maven repo needs `mvn validate` once before `package`, so the audit jar gets installed. This is documented in the README and CI.

---

## Migration Journey

### Phase 1: Codebase Analysis
**Status**: ✅ Completed
**Duration**: about 20 s (`rgctl discover` with cfg, security, taint, dashboard, harmonic and migration hints)

- **Files Analyzed**: 1,233 (3 parse failures, all vendored)
- **Dependencies Mapped**: 35,860 nodes, 125,570 edges
- **Communities Identified**: 23,882 in total (modularity 0.45); 33 of them are coolstore/Java communities
- **Hotspots Detected**: the top PageRank nodes are all vendored JS or external (`Date`); the app has no high-complexity functions (avg complexity 1.0)
- **Security**: 8 possible secrets. The one taint flow is in vendored `matchHeight/gulpfile.js`, which is not application code.

Key insights:
- The Java-only migration order from `migration_plan.json` is model → utils → service → rest → front-end services and controllers → weblogic stubs → persistence. The execution followed this order.
- The app is a small, low-complexity monolith, so a single big-bang conversion carries little risk.

### Phase 2: Requirements Gathering
**Status**: ✅ Completed (autonomous: requirements came from the repo evidence above instead of asking the user)

- **Source Technology**: Java EE 7 (JAX-RS, CDI, EJB 3.2, JPA 2.1, JMS 2.0, JSON-P, RMI) on JBoss EAP 7.4, Java 8, WAR
- **Target Technology**: Quarkus 3.15.x LTS, Java 21, Hibernate ORM 6, Quarkus REST plus Jackson, SmallRye Reactive Messaging, quarkus-flyway, quarkus-oidc, SmallRye Health
- **Migration Strategy**: big bang, in place, single module
- **Deployment Platform**: Red Hat OpenShift (UBI9 images)
- **Output**: `mig-prompt-workspace/migration-prompt.md` (9 REST endpoints that must stay unchanged, design decisions, assumptions)

### Phase 3: Migration Planning
**Status**: ✅ Completed

- **User Stories**: 5
- **Task Groups**: 10
- **Subtasks**: 83
- **Outputs**: `mig-plan-workspace/tasks.md`, `mig-plan-workspace/UserStory.md`

### Phase 4: Migration Execution
**Status**: ⚠️ Completed with 2 deferred subtasks

**Completion Statistics**:
- User Stories: 5 of 5 (100%)
- Task Groups: 10 of 10 (100%)
- Subtasks: 81 of 83 (97.6%)

**Deferred (not failed) subtasks**:
- 8.3: JaCoCo coverage report (`quarkus-jacoco` not added)
- 8.5: performance baseline (startup time and RSS not measured)

**Process deviation**: the `mig-test-gen`, `mig-containerize` and `mig-deploy` skills were **not loaded** during execution because the execution context was running low. The tests, Containerfile and manifests they would have produced were written directly from the plan's task list instead. Re-running those skills against the result is recommended (see Recommendations).

---

## Technical Details

### Source Application Analysis

**Technology Stack** (before): Java 8, Java EE 7 (`javaee-api` 7.0, provided), JBoss EAP 7.4 `standalone-full-ha`, PostgreSQL through the server-side JNDI datasource `java:jboss/datasources/CoolstoreDS`, ActiveMQ Artemis `topic/orders`, Flyway 4.1.2, `audit-logging-library` 1.0.0 (system scope), AngularJS 1.x and PatternFly front end (bower), Keycloak 20 front-end login.

**Architecture Pattern**: a layered monolith (rest → service → model/persistence) with asynchronous order processing over a JMS topic and HTTP session clustering.

**Key Components**: `CartEndpoint` (session-scoped), `ProductEndpoint`, `OrderEndpoint`; `ShoppingCartService` (stateful), `ShippingService` (remote EJB), `PromoService`, `CatalogService`, `OrderService`; `OrderServiceMDB` and `InventoryNotificationMDB`; `DataBaseMigrationStartup` (@Singleton @Startup Flyway).

**Code Metrics**: 30 Java source files in `src/main/java`, primary language Java, plus about 1,200 vendored JS/CSS files.

### Target Application Design

**Technology Stack** (after): Quarkus 3.15.1 platform BOM, Java 21. Extensions: `rest`, `rest-jackson`, `hibernate-orm`, `jdbc-postgresql`, `flyway`, `messaging` (in-memory), `oidc`, `smallrye-health`; test: `junit5`, `rest-assured`, `jdbc-h2`.

**Architecture Pattern**: the same layered monolith, packaged as a Quarkus fast-jar, stateless apart from the in-memory cart store and in-process messaging.

**Key Changes**:
- EJB → CDI (`@ApplicationScoped`, `@Transactional`, `@Startup`/`StartupEvent`)
- JMS topic and MDBs → `Emitter<String>` with `@Incoming("orders")` consumers (broadcast to both consumers)
- Remote EJB `ShippingServiceRemote` → local `ShippingService` bean
- `persistence.xml` and JNDI datasource → `application.properties` with env-var overrides; Flyway is managed by Quarkus
- `@SessionScoped` cart and `<distributable/>` → `CartStore`/`InMemoryCartStore` keyed by `cartId`
- Audit library 1.0.0 `FileSystemAuditLogger` → 2.0.0 `StreamableAuditLogger` (TCP, `AUDIT_STREAM_HOST`/`PORT`, async, tolerant of failures) through `AuditLoggerProducer`
- Front end moved from `src/main/webapp` to `src/main/resources/META-INF/resources`; `index.jsp` → `index.html`

### Files Changed

67 non-UI files changed (+1,694 / −751). The UI move adds about 2,500 pure renames. Key files:
- `pom.xml`: WAR/Java EE 7 → Quarkus BOM 3.15.1, Java 21; audit lib 2.0.0 installed from `lib/`; tests switched on
- `src/main/resources/application.properties`: new; datasource, Flyway, messaging, OIDC, audit and `%test` profile settings
- `service/ShoppingCartService.java`, `CartEndpoint.java`: stateless and backed by `CartStore`
- `service/CartStore.java`, `InMemoryCartStore.java`, `AuditLoggerProducer.java`: new
- `service/OrderServiceMDB.java`, `InventoryNotificationMDB.java`, `ShoppingCartOrderProcessor.java`: JMS → Reactive Messaging
- `service/ShippingService.java`: the remote EJB is now a local bean (`ShippingServiceRemote` deleted)
- `utils/StartupListener.java`, `Producers.java`, `Transformers.java`: Quarkus lifecycle and Jakarta JSON
- Deleted: `persistence.xml`, `web.xml`, `Resources.java`, `DataBaseMigrationStartup.java`, `weblogic/**`, `lib/audit-logging-library-1.0.0.jar`
- `README.md`, `.github/workflows/build.yml` (replaces the Azure WAR workflow), `.gitignore`

Full list: `mig-execute-workspace/EXECUTION_REPORT.md`

### Integration Work Completed

**Testing** (13 tests, all passing; the build was checked again independently by the orchestrator):
- Unit: `PricingTest` (4: promotions and shipping tiers), `TransformersTest` (1), `AuditLoggerTest` (2, including sending to a real TCP server)
- Integration/contract: `RestContractTest` (6 `@QuarkusTest` tests on H2 with Flyway: products, cart isolation and pricing, add/merge/delete, checkout → persisted order and reduced inventory, static UI, health)
- End-to-end: `src/test/e2e/smoke.sh` (curl smoke test for a deployed instance; **not run**)
- Coverage: not measured (subtask 8.3 deferred)

**Containerization**:
- 1 component containerized: `Containerfile`, multi-stage, `ubi9/openjdk-21` builder → `ubi9/openjdk-21-runtime`, non-root UID 185, port 8080
- `.containerignore`, `.dockerignore` and `compose.yaml` (app plus PostgreSQL) for local runs
- Image **not built** (no podman or docker available)

**Deployment** (`deploy/openshift/`, Kustomize):
- 11 base manifests: Deployment, Service, Route, ConfigMap, Secret (template), ImageStream, BuildConfig, HPA, NetworkPolicy, PostgreSQL 15 (Deployment, PVC, Service), kustomization
- `overlays/dev` and `overlays/prod`
- Statically checked (16 YAML documents parse correctly); **not applied** (no oc or cluster)
- Replicas are pinned to 1 (HPA max 1) because carts and messaging are in-memory

---

## Execution Statistics

### Timeline (approximate, UTC 2026-10-23)
- **Total Duration**: about 15 min
- **Phase 0–1 (Init + Analysis)**: about 1 min
- **Phase 2–3 (Requirements + Planning)**: about 4 min
- **Phase 4 (Execution, including JDK/Maven bootstrap and tests)**: about 8 min
- **Phase 5 (Verification + Reporting)**: about 2 min

### Success Metrics
- **User Stories Completed**: 5/5 (100%)
- **Task Groups Completed**: 10/10 (100%)
- **Subtasks Completed**: 81/83 (97.6%)
- **Tests**: 13/13 passing; `mvn package` BUILD SUCCESS

### Failure Analysis
- **Failed Tasks**: 0
- **Deferred**: 2 (coverage tooling and performance baseline), deprioritized because execution context was running low
- **Unverified because of the environment**: image build, cluster deploy, smoke test

---

## Deliverables

```
repo-root/
├── .rgctl/                       # Phase 1 rgctl index + dashboard + migration_plan.json (gitignored, regenerable)
├── mig-prompt-workspace/
│   └── migration-prompt.md
├── mig-plan-workspace/
│   ├── tasks.md
│   └── UserStory.md
├── mig-execute-workspace/
│   ├── EXECUTION_REPORT.md
│   ├── execution-log.md
│   └── outputs/tests/surefire-summary.txt
├── migiq-workspace/
│   ├── orchestration-log.md
│   └── MIGRATION_REPORT.md       # this file
├── Containerfile, compose.yaml, .containerignore, .dockerignore
├── deploy/openshift/{base,overlays/dev,overlays/prod}/
├── src/main/resources/application.properties
├── src/test/java/com/redhat/coolstore/*Test.java
└── src/test/e2e/smoke.sh
```

`.konveyor/analysis.json` was used as an input and left untracked on purpose.

### Key Documents
1. **Migration Plan**: `mig-plan-workspace/tasks.md`
2. **Execution Report**: `mig-execute-workspace/EXECUTION_REPORT.md`
3. **Migration Prompt (spec + design)**: `mig-prompt-workspace/migration-prompt.md`
4. **User Stories**: `mig-plan-workspace/UserStory.md`
5. **Codebase Analysis**: `.rgctl/migration_plan.json`, `.rgctl/dashboard/index.html` (regenerate with `rgctl discover .`)
6. **Orchestration Log**: `migiq-workspace/orchestration-log.md`

---

## Recommendations

### Immediate Next Steps
1. **Build the image and deploy to a dev namespace**: `podman build -f Containerfile .` (or use the BuildConfig), then `oc apply -k deploy/openshift/overlays/dev`, then run `src/test/e2e/smoke.sh` against the Route.
2. **Replace the Secret template** in `deploy/openshift/base/secret.yaml` (it contains the placeholder `change-me`) with Sealed Secrets or External Secrets before any shared or prod environment.
3. **Point the audit stream** (`AUDIT_STREAM_HOST`/`AUDIT_STREAM_PORT`) at the real collector. The default `localhost:5170` only logs warnings.
4. **Finish the deferred subtasks**: add `quarkus-jacoco` (target ≥ 70% line coverage) and record a startup/RSS baseline against the EAP deployment.
5. **Run the skipped integration skills** (`mig-test-gen`, `mig-containerize`, `mig-deploy`) against the migrated code to check the hand-written test, container and deploy files against their checklists.

### Long-term Recommendations
1. **Externalize state before scaling out**: add a Redis or Red Hat Data Grid `CartStore` implementation and move the `orders` channel to AMQ Broker (AMQP) or AMQ Streams (Kafka). Both are config or adapter changes behind the existing interfaces. Only then raise replicas and HPA limits.
2. **Turn on OIDC in staging** (`OIDC_ENABLED=true`, Keycloak `eap` realm from `realm-export.json`) and decide whether checkout endpoints should require authentication.
3. **Replace the vendored bower front end** with an npm-managed build, or move it out of the repo. It makes up about 90% of the files and of rgctl's graph noise, and holds the only taint finding.
4. Consider a Quarkus native build (Mandrel/UBI) once the extension set is stable, to cut startup time and memory on OpenShift.

---

## Conclusion

CoolStore no longer depends on JBoss EAP or the Java EE 7 server stack. It is now a Quarkus 3.15 LTS application on Java 21 with externalized configuration, health probes, in-process messaging, the audit library upgraded to the required 2.x API, and no HTTP session clustering. The build succeeds and all 13 tests pass, including an end-to-end checkout through REST, messaging and persistence. The Konveyor mandatory finding and all custom audit-logging rules are resolved.

**Migration Status**: ⚠️ Partial Success. The code migration is finished and verified. The remaining work is operational: build and deploy on a real OpenShift cluster, replace the Secret template, connect the audit collector, add coverage and a performance baseline, and externalize cart and messaging state before running more than one replica.

---

**Report Generated**: 2026-10-23 14:32 UTC
**Generated by**: MigIQ Orchestrator
