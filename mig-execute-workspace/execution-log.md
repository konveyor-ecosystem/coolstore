# Migration Execution Log

**Started**: 2026-10-23 14:25 UTC
**Migration**: Java EE 7 / JBoss EAP 7.4 (Java 8, WAR) → Quarkus 3.15.1 LTS / Java 21 / OpenShift
**Plan Version**: commit 7967f31 (Phase 2-3)

---

## Execution Timeline

### Tooling
Installed Temurin JDK 21.0.13+9 and Maven 3.9.9 in /tmp/tools, with the Maven repo at /tmp/m2. python3 has no PyYAML/pip, so YAML was validated with SnakeYAML 2.2 (from /tmp/m2) through a single-file Java program kept in /tmp.

### TG1 Build Foundation: ✅
Rewrote pom.xml (jar packaging, quarkus-bom 3.15.1, release 21, surefire 3.5, no maven.test.skip). maven-install-plugin `install-file` installs lib/audit-logging-library-2.0.0.jar during `validate` (no system scope); 1.0.0 jar deleted. Removed web.xml (`<distributable/>`), beans.xml, persistence.xml. javax → jakarta.
**Issue**: on a clean local repo, Maven resolves dependencies before validate-phase plugins run inside a single `package` invocation, so the first `mvn package` fails. Workaround: run `mvn -B validate` once first. This is documented in the README and CI.

### TG2 Persistence/Flyway: ✅
Removed Resources.java and DataBaseMigrationStartup. quarkus-flyway uses migrate-at-start and baseline-on-migrate (baseline-version 0). The datasource is configured from DB_* env vars. The ID generator is pinned to `hibernate_sequence` with allocationSize 1. Removed `@XmlRootElement` and the stray `@Column(TOTAL_PRICE)`. The test profile uses H2 in PostgreSQL mode, and V1_1/V1_2 apply unchanged.

### TG3 Services: ✅
EJBs became @ApplicationScoped, with @Transactional on writes. ShippingServiceRemote and its JNDI lookup were deleted; ShippingService is now injected locally. ShoppingCartService takes carts from the `CartStore` interface, implemented by `InMemoryCartStore` (a ConcurrentHashMap keyed by cartId), which fixes the bug where cartId was ignored.

### TG4 Messaging: ✅
`@Channel("orders") @Broadcast Emitter<String>` publishes to two consumers: OrderServiceMDB (`@Incoming @Blocking @Transactional`, persists the order and decrements inventory) and InventoryNotificationMDB (low-stock warning). The WebLogic t3/JMS code was removed.

### TG5 Audit 2.0.0: ✅
Added AuditLoggerProducer, which lazily creates a StreamableAuditLogger (TCP, AUDIT_STREAM_HOST/PORT). OrderService.save builds `AuditEvent` with the record constructor (ORDER_CREATED, SUCCESS/FAILURE) and sends it with `logEventAsync(...).exceptionally(...)`. Events are skipped if no collector is reachable. No builder/logEvent/logSuccess/logFailure calls remain.

### TG6 REST/OIDC: ✅
CartEndpoint is @ApplicationScoped (no session). Paths are unchanged. quarkus-oidc runs as a service app with `tenant-enabled=${OIDC_ENABLED:false}` and a permit policy on `/*`.

### TG7 UI/Cleanup: ✅
The UI moved to META-INF/resources, index.jsp became index.html, and health.jsp was replaced by /q/health. weblogic.* was deleted; StartupListener now observes StartupEvent/ShutdownEvent. A grep gate finds no javax./weblogic/InitialContext/System.out in src/main/java.

### TG8 Tests: ✅ (with 2 deferrals)
`mvn -B -Dmaven.repo.local=/tmp/m2 package` → BUILD SUCCESS, 13 tests run, 0 failures, 0 errors:
- PricingTest (4)
- TransformersTest (1)
- AuditLoggerTest (2; one with an embedded TCP ServerSocket receiving ORDER_CREATED)
- RestContractTest (6 @QuarkusTest: products, cart isolation/pricing, add/merge/delete, checkout → order persisted + inventory decremented, static UI + health)

Added the e2e script src/test/e2e/smoke.sh.
**Deferred**: 8.3 JaCoCo coverage (quarkus-jacoco not added) and 8.5 performance baseline (not measured).

### TG9 Containerize: ✅ (static)
Containerfile (ubi9/openjdk-21 build stage → ubi9/openjdk-21-runtime, USER 185, EXPOSE 8080), .containerignore/.dockerignore, compose.yaml. podman is unavailable, so the image was **not built**.

### TG10 Deploy: ✅ (static)
deploy/openshift/base (Kustomize): ConfigMap, Secret template, PostgreSQL 15 Deployment+PVC+Service, ImageStream, BuildConfig, Deployment (probes, resources, replicas 1), Service, Route edge, HPA (max 1), NetworkPolicy; overlays dev/prod. SnakeYAML parse: 14 files, 16 documents OK. oc is unavailable, so nothing was applied.

### Docs/CI
README rewritten. The Azure WAR workflow was replaced by .github/workflows/build.yml (Temurin 21, mvn validate + package).

---

## Summary
- **Total User Stories**: 5 (5 completed; US5 with deferrals)
- **Total Task Groups**: 10 (10 completed)
- **Total Subtasks**: 83
- **Completed**: 81
- **Failed**: 0
- **Deferred**: 2 (8.3, 8.5)
