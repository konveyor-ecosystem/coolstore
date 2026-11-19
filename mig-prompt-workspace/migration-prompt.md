# Migration Prompt

## Current Application Summary

The application is **coolstore** (APP_ID=7), a Java EE 7 monolithic e-commerce
storefront packaged as a WAR (`com.redhat.coolstore:monolith:1.0.0-SNAPSHOT`). It is
deployed on JBoss EAP 7.4 in a clustered, high-availability topology (two nodes running
`standalone-full-ha.xml`), backed by PostgreSQL and fronted by Keycloak 20.0.5 for
OIDC-based authentication. Code-graph analysis (rgctl) indexed 52 application files
into 1,023 graph nodes with 2,648 edges and analyzed 244 functions (average complexity
1.3; only 1 high-complexity function), indicating a small, low-complexity codebase.

The business logic is organized in classic Java EE layers: JAX-RS REST endpoints
(`CartEndpoint`, `OrderEndpoint`, `ProductEndpoint`), CDI services
(`CatalogService`, `ProductService`, `PromoService`, `ShippingService`,
`ShoppingCartService`, `ShoppingCartOrderProcessor`, `OrderService`), JMS
message-driven beans (`OrderServiceMDB`, `InventoryNotificationMDB`) consuming the
`topic/orders` ActiveMQ topic, and JPA entities
(`CatalogItemEntity`, `InventoryEntity`, `Order`, `OrderItem`, `Product`,
`Promotion`, `ShoppingCart`, `ShoppingCartItem`) persisted via Hibernate/JPA with
Flyway-managed schema migrations. A remote EJB interface
(`ShippingServiceRemote`) plus WebLogic compatibility shims
(`weblogic.application.ApplicationLifecycleListener`,
`weblogic.i18n.logging.NonCatalogLogger`) couple the code to legacy server APIs.
The frontend is an AngularJS 1.x single-page app (~460 LOC in
`src/main/webapp/app/`) served from the WAR with vendored libraries under
`bower_components/` — graph hotspots (`logout`, `login`, `addToCart`, `checkout`)
confirm checkout/auth flows are the most connected code paths.

**Technology Stack:**
- Java 8 (source/target 1.8), Java EE 7 (CDI, JAX-RS, JPA, EJB, JMS 2.0)
- JBoss EAP 7.4 (clustered HA, two nodes), ActiveMQ Artemis JMS
- PostgreSQL (Flyway 4.1.2 schema migrations), Keycloak 20.0.5 (OIDC)
- Maven WAR build; custom `audit-logging-library` 1.0.0 as a system-scoped JAR in `lib/`
- AngularJS 1.x frontend with bower-managed vendored dependencies

**Architecture:**
- Monolithic 3-tier architecture (WAR: REST API + services + JPA persistence + SPA frontend)
- In-JVM CDI wiring with producers (`Producers`, `Resources`), startup utilities
  (`StartupListener`, `DataBaseMigrationStartup`, `Transformers`)
- Integration points: PostgreSQL datasource (CoolstoreDS), JMS topic `topic/orders`,
  Keycloak OIDC realm, remote EJB/RMI (`ShippingServiceRemote`)
- WebLogic compatibility shims compiled into the source tree

**Scale:**
- 30 Java files / ~1,785 LOC Java; 244 functions analyzed
- 819 graph communities; 9 circular dependencies detected
- 2 potential secrets and 1 vulnerable taint flow flagged by security analysis
- 14-step migration order exported by rgctl (Hybrid Default preset, scheduled order)

## Target Platform and Technologies

**Target Stack:**
- Quarkus 3.x on Java 21 (LTS)
- Jakarta EE 10 namespaces (`javax.*` → `jakarta.*`), CDI via ArC
- REST endpoints via quarkus-rest (RESTEasy Reactive), keeping the existing REST contract
- Hibernate ORM (Panache optional) + `quarkus-flyway` against PostgreSQL
- `quarkus-oidc` integration with the existing Keycloak realm
- Quarkus Messaging / SmallRye JMS (Artemis) replacing JMS MDBs
- `audit-logging-library` upgraded 1.0.0 → 2.0.0 (the repo's own mandatory
  `audit-logging-migration` ruleset requires v2.x on Java 21) — replace the
  system-scoped JAR with a proper Maven dependency
- WebLogic shims removed; `ShippingServiceRemote` remote EJB replaced by a REST or
  in-process service boundary
- Migration rationale: cloud-native, container-first runtime with fast startup,
  first-class OpenShift support, and a standard Konveyor-supported path for Java EE
  workloads; Java 21 required by the audit-logging-library 2.x upgrade rule

**Deployment Platform:**
- Red Hat OpenShift 4.x (Kubernetes-based container platform)

**Infrastructure Requirements:**
- Container runtime: Podman (Docker-compatible)
- Orchestration: Kubernetes on OpenShift
- CI/CD: OpenShift Pipelines (Tekton) or GitOps
- Database: managed/-containerized PostgreSQL; Keycloak operator or existing realm
- The `pom.xml` already contains a placeholder (`<!-- TODO: Add OpenShift profile here -->`)

## Migration Approach

**Strategy:** Phased migration (strangler-fig-lite) — migrate the monolith runtime
in-place through vertical slices, then containerize and deploy to OpenShift.

**Reasoning:**
- Small codebase (~30 Java files, low complexity) makes a big-bang rewrite feasible,
  but phased delivery reduces risk to the checkout revenue path and gives the team
  early OpenShift deployment experience
- The rgctl migration order (Hybrid Default, scheduled) provides a dependency-aware
  sequence: frontend/app scaffold → WebLogic shims removal → model → persistence →
  REST → utils → services

**Phases:**
1. Foundation & Baseline (characterization tests, CI, tooling)
2. Runtime migration to Quarkus (build, CDI, JAX-RS, JPA, Flyway, Java 21, audit-logging 2.0.0)
3. Messaging & security (JMS MDBs, Keycloak OIDC, session handling, HA)
4. Containerization & OpenShift deployment, cutover

**Key Considerations:**
- `javax.*` → `jakarta.*` namespace migration across all Java sources
- EJB lifecycle/transactions → CDI + Quarkus transaction management
- JMS MDBs (`@MessageDriven`) → Quarkus messaging consumers; preserve `topic/orders`
  semantics and dual-node processing behavior
- Keycloak OIDC adapter (web.xml security constraints) → `quarkus-oidc` extension
- Remote EJB `ShippingServiceRemote` → REST boundary (removes RMI coupling flagged
  by tech-discovery tags)
- Remove WebLogic shim classes and the WebLogic application lifecycle listener usage
- System-scoped JAR (`lib/audit-logging-library-1.0.0.jar`) → Maven dependency on 2.0.0
- AngularJS frontend served as static resources initially; bower components pinned
  via CDN/npm, no functional frontend rewrite in scope

## Required Deliverables

### 1. Containerization
- All components containerized with Podman-compatible multi-stage Containerfiles
- Optimized image sizes (multi-stage build, Quarkus fast-jar)
- Security scanning of container images
- Registry strategy: OpenShift internal registry

### 2. OpenShift Deployment
- Kubernetes/OpenShift manifests (Deployment, Service, Route, ConfigMap, Secret)
- Rolling deployment strategy; readiness/liveness probes on Quarkus health endpoints
- Resource limits and autoscaling configuration
- Externalized configuration: database URL/credentials, Keycloak URL, JMS broker
  settings via ConfigMaps/Secrets
- PostgreSQL and Keycloak (or Artemis) provisioned on-platform or bound externally

### 3. Test Coverage
- **Characterization tests** capturing current behavior before migration: cart
  pricing rules (PromoService/ShippingService), checkout flow, REST endpoint
  contracts, order JMS processing
- **Unit tests** for all migrated services (≥80% coverage on new code)
- **Integration tests** for persistence (Flyway + PostgreSQL), JMS consumers, OIDC
- **End-to-end tests** covering the login → browse → add-to-cart → checkout journey
- **Performance tests**: baseline current EAP metrics, validate parity on Quarkus

### 4. Detailed Migration Plan
- Phase-by-phase breakdown with task lists, owners, and dependencies
- Risk assessment and mitigation strategies (rollback procedures per phase)
- Monitoring and observability setup (metrics, health, logging via audit-logging-library 2.x)
- Documentation: migration guide, runbook, README updates
- Training plan for the team on Quarkus/OpenShift

## Success Criteria

- All components running on OpenShift from CI/CD-managed container images
- Functional parity: complete login → browse → cart → checkout journey with Keycloak
- JMS order processing works with dual-instance deployment (clustering semantics preserved)
- Characterization + unit + integration + E2E test suites pass; coverage targets met
- WebLogic shims, remote EJB, and system-scoped JAR eliminated; audit-logging-library 2.0.0 active on Java 21
- Team trained; documentation and runbooks complete

## Constraints and Risks

**Constraints:**
- Java 21 is required by the audit-logging-library 2.x mandatory ruleset
- Preserve the existing REST API contract consumed by the AngularJS frontend
- Preserve Flyway schema history (existing V1_1/V1_2 migrations must remain applicable)

**Risks:**
- JMS semantics drift when moving MDBs to Quarkus messaging (mitigate with
  characterization tests on order flow; dual-node processing test)
- Keycloak session/token handling differences between adapters (mitigate with E2E
  auth tests)
- 9 circular dependencies and 2 flagged secrets complicate refactoring (remediate
  secrets before containerization; refactor cycles opportunistically)
- Single vulnerable taint flow flagged by rgctl (remediate during runtime migration)

---

**Request**: Create a comprehensive migration plan following the above requirements.
The plan should include specification, design, task breakdown, and user stories
suitable for immediate execution by the development team.
