# MigIQ Orchestration Log

**Started**: 2026-10-23 13:39 UTC
**Project**: coolstore-monolith (repo: /workspace/repo, branch konveyor/migration-1792762740)

---

## Orchestration Timeline

### Phase 0: Initialization - COMPLETED
**Timestamp**: 2026-10-23 13:39 UTC
**Working Directory**: /workspace/repo
**Target Technology**: Quarkus 3 (Java 21) on Red Hat OpenShift. This is **inferred**: no explicit target was given. The evidence:
- `rules/rule.yaml` custom Konveyor rules are labelled `konveyor.io/source=openjdk8`, `konveyor.io/target=quarkus3`, and they require audit-logging-library 2.0.0 plus Java 21.
- `.konveyor/analysis.json` cloud-readiness finding: `<distributable/>` HTTP session replication (mandatory).
- `pom.xml` contains `TODO: Add OpenShift profile`.
**Environment limitation**: there is no JDK, Maven, container engine or `oc` CLI in the sandbox. Builds, tests, image builds and deployments cannot be run here. Everything is validated statically.

---

### Phase 1: Codebase Analysis - COMPLETED
**Duration**: ~18s (discover) plus manual review
**Command**: `rgctl discover . --with-cfg --with-security --with-taint --with-dashboard --with-harmonic --export-migration-hints --migration-preset hybrid_default --migration-order scheduled`
**Outputs**:
- `.rgctl/` (in-repo; git-ignored, 5.9 MB): graph snapshot, dashboard and `migration_plan.json` (155 steps)

**Statistics**:
- Files indexed: 1232 (3 failed). Nodes/Edges: 35,856 / 125,562
- Functions: 26,841 (avg complexity 1.0, 0 high). Communities: 23,881 (modularity 0.45). Circular deps: 317
- Security: 8 potential secrets. Taint: 1 vulnerable flow
- More than 95% of the graph is vendored `src/main/webapp/bower_components` JavaScript. PageRank hotspots (Date, qunit join, ...) are all vendor code.

**Java scope (the actual migration surface)**: 30 Java files in 6 packages. The migration_plan order for the Java communities is: model (step 15), utils (16), service (17), rest (18), weblogic.* stubs (31, 39), persistence (47).

**Key findings**:
- EJBs: @Stateless CatalogService, OrderService, ProductService and ShoppingCartOrderProcessor; @Stateful ShoppingCartService; @Stateless @Remote ShippingService, looked up by WildFly JNDI `ejb:/ROOT/ShippingService!...`; @Singleton @Startup DataBaseMigrationStartup (Flyway 4 API).
- JMS: @MessageDriven OrderServiceMDB on `topic/orders`. InventoryNotificationMDB uses a WebLogic T3 JNDI factory (dead code). ShoppingCartOrderProcessor uses JMSContext and @Resource topic.
- WebLogic stubs: `weblogic.application.*`, `weblogic.i18n.logging.NonCatalogLogger`, StartupListener.
- JPA `persistence.xml` with JNDI datasource `java:jboss/datasources/CoolstoreDS`. Flyway SQL in `db/migration`. Uses `hibernate_sequence`.
- JAX-RS under `/services`. @SessionScoped CartEndpoint. `web.xml <distributable/>`.
- System-scoped `lib/audit-logging-library-1.0.0.jar`; custom rules require 2.0.0, Java 21 and `StreamableAuditLogger`.
- `Order.java` has a dangling `@Column(name="TOTAL_PRICE")` annotation that attaches to `itemList`.
- Frontend: AngularJS plus Keycloak JS (`keycloak.json`) and JSPs (`index.jsp`, `health.jsp`).

---
