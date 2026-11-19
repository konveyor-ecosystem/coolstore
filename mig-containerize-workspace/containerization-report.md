# Containerization Report: Coolstore Monolith

**Branch:** `konveyor/migration-1795103297` · **Date:** 2026-11-19

## Executive Summary

- **Application:** coolstore — Java EE 7 e-commerce monolith (`com.redhat.coolstore:monolith:1.0.0-SNAPSHOT`, WAR)
- **Language/Framework:** Java 8 (source/target), Java EE 7 (CDI, JAX-RS, JPA, EJB, JMS 2.0), AngularJS 1.x SPA, Maven
- **Base Images:** `registry.access.redhat.com/ubi8/openjdk-11` (builder + runtime), WildFly 26.1.3.Final layered as app server
- **Deployment Target:** OpenShift 4.x (manifests in `openshift/`)
- **Build Validation:** `mvn package` → `target/ROOT.war` (16 MB) builds cleanly with the repo's system-scoped `audit-logging-library` JAR; image build not executable in this environment (no podman/docker — see Known Issues)
- **Migration Complexity:** Medium (full Java EE stack incl. EJB MDBs + JMS; JNDI datasource; WebLogic shims; Keycloak client-side OIDC)

## Graph Analysis Findings

rgctl (`discover --with-cfg --with-security --with-taint --with-harmonic`) indexed **1,277 files → 28,410 nodes / 86,794 edges** (the count includes vendored `bower_components`; the app's own Java code is 30 files / ~1,785 LOC). 15 application communities map to the layers in `migration_plan.json` (rest, service, utils, model, persistence, webapp, weblogic shims).

### External Dependencies
| Dependency | Evidence | Container strategy |
|---|---|---|
| PostgreSQL | `persistence.xml` JNDI `java:jboss/datasources/CoolstoreDS`; Flyway `V1_1/V1_2` migrations | StatefulSet (`openshift/03-postgres.yaml`) or external managed DB |
| Keycloak 20.0.5 (OIDC) | `src/main/webapp/keycloak.json` (public client `eap-app`, realm `eap`); `realm-export.json` | Deployment + Service (`openshift/04-keycloak.yaml`) |
| JMS topic `topic/orders` | `OrderServiceMDB` (activation config), `InventoryNotificationMDB` | Artemis embedded in WildFly `standalone-full.xml`; topic bound at build via `scripts/wildfly-config.cli` |

### Platform-Specific Code Identified
- **JNDI datasource** `java:jboss/datasources/CoolstoreDS` (server-managed, not in WAR) → re-created in-image via CLI with **env-driven** connection: `DB_HOST/DB_PORT/DB_NAME` (ConfigMap), `DB_USER/DB_PASSWORD` (Secret)
- **Hardcoded Keycloak URL** `http://localhost:8081/` in `keycloak.json` → patched at container start from `KEYCLOAK_AUTH_SERVER_URL` (entrypoint `sed`)
- **WebLogic coupling** — `StartupListener` extends `weblogic.application.ApplicationLifecycleListener` (shimmed in-repo); `InventoryNotificationMDB` references `weblogic.jndi.WLInitialContextFactory`. Harmless at runtime (shims compiled into the WAR); removal is Task Group 3 of the migration plan
- **System-scoped JAR** `lib/audit-logging-library-1.0.0.jar` → included via build context (`COPY lib/`); verified to build
- **Filesystem writes** — none outside the app server's own `standalone/{log,tmp,data,deployments}` dirs → emptyDir volume under read-only root FS
- **Security findings** — rgctl flagged 3 potential secrets (in vendored `bower_components` JS, not app code) and 1 vulnerable taint flow (documented in migration plan TG1/TG2, remediation tracked there)

## Containerization Strategy

### Base Image Selection
- **Selected:** `registry.access.redhat.com/ubi8/openjdk-11` for both stages (verified available, no registry login required)
- **App server:** WildFly 26.1.3.Final — the last `javax` (Jakarta EE 8) line and community upstream of JBoss EAP 7.4; runs the unmodified WAR today. EAP 7.4 OpenShift images require `registry.redhat.io` credentials (terms-gated) and were not usable here
- **JDK 11** mirrors the EAP 7.4 support matrix; the planned Quarkus 3/Java 21 migration (tasks TG2) will move the image to `ubi9/openjdk-21` + fast-jar — see Next Steps

### Build Strategy
Multi-stage (compiled stack):
1. **Builder** — UBI8 OpenJDK 11 + pinned Apache Maven 3.9.9 → `mvn package` → exploded `ROOT.war`
2. **Runtime** — UBI8 OpenJDK 11 + WildFly 26.1.3 + PostgreSQL JDBC 42.7.4 driver (module) + offline CLI config (`datasource CoolstoreDS` with `${env.*}` expressions, `jms-topic orders` with `topic/orders` + `java:/topic/orders` entries) → exploded WAR at `/opt/app/ROOT.war` (read-only)

### Security Hardening Applied
- ✅ Multi-stage build (build tools excluded from runtime image)
- ✅ Non-root execution, UID 1001, group 0 with `g+rwX` (OpenShift-compatible)
- ✅ Read-only root filesystem (Deployment) + emptyDir for `standalone/`
- ✅ All capabilities dropped; `runAsNonRoot`; `seccompProfile: RuntimeDefault`; no privilege escalation
- ✅ Secrets externalized (OpenShift Secret / compose env), never baked into image layers
- ✅ Pinned component versions (Maven, WildFly, JDBC driver); UBI base from Red Hat's supported registry
- ✅ `HEALTHCHECK` + readiness/liveness probes
- ⏳ Vulnerability scan (`grype`) — not executable in this environment; run `grype coolstore-monolith:latest` after building

## Generated Artifacts

| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage UBI8 build (podman-compatible) |
| `scripts/wildfly-config.cli` | Offline WildFly config: driver, datasource, JMS topic |
| `scripts/entrypoint.sh` | Stages deployment, patches `keycloak.json`, starts WildFly |
| `.dockerignore` | Minimal build context |
| `openshift/01-configmap.yaml` | Non-sensitive config (DB host/port/name, Keycloak URL) |
| `openshift/02-secret.yaml` | DB credentials (placeholders — replace before apply) |
| `openshift/03-postgres.yaml` | PostgreSQL StatefulSet + headless Service (10Gi PVC) |
| `openshift/04-keycloak.yaml` | Keycloak 20.0.5 Deployment + Service (`eap` realm import) |
| `openshift/05-deployment.yaml` | App Deployment (2 replicas, hardened securityContext, probes) |
| `openshift/06-service.yaml` | ClusterIP Service |
| `openshift/07-route.yaml` | Edge-TLS Route |
| `podman-compose.yml` | Local dev stack: app + postgres + keycloak |

## Deployment Instructions

### Local (podman-compose)
```bash
podman login registry.redhat.io            # for rhel9/postgresql-15
podman-compose up -d
curl -f http://localhost:8080/health.jsp   # app health
open http://localhost:8080                 # SPA; Keycloak at http://localhost:8081
```

### OpenShift
```bash
oc new-project coolstore
oc create configmap keycloak-realm --from-file=realm-export.json
oc apply -f openshift/02-secret.yaml       # edit placeholders first!
oc apply -f openshift/01-configmap.yaml -f openshift/03-postgres.yaml -f openshift/04-keycloak.yaml
podman build -t coolstore-monolith:latest .
podman tag coolstore-monolith:latest image-registry.openshift-image-registry.svc:5000/coolstore/coolstore-monolith:latest
podman login -u $(oc whoami) -p $(oc whoami -t) image-registry.openshift-image-registry.svc:5000
podman push image-registry.openshift-image-registry.svc:5000/coolstore/coolstore-monolith:latest
oc apply -f openshift/05-deployment.yaml -f openshift/06-service.yaml -f openshift/07-route.yaml
oc rollout status deployment/coolstore
curl -f https://$(oc get route coolstore -o jsonpath='{.spec.host}')/health.jsp
```
Flyway (`DataBaseMigrationStartup`, `@Singleton @Startup`) applies `V1_1/V1_2` automatically on boot against `CoolstoreDS`.

## Configuration Reference

| Variable | Description | Source | Required |
|---|---|---|---|
| `DB_HOST` / `DB_PORT` | PostgreSQL host/port | ConfigMap | Yes |
| `DB_NAME` | PostgreSQL database | ConfigMap | Yes |
| `DB_USER` / `DB_PASSWORD` | DB credentials | Secret | Yes |
| `KEYCLOAK_AUTH_SERVER_URL` | OIDC auth server for the SPA adapter | ConfigMap | Yes |

**Resources (app pod):** 250m/512Mi requests, 1000m/1Gi limits. **Probes:** readiness `/health.jsp` (20s initial), liveness `/health.jsp` (60s initial). **Keycloak:** admin creds via Deployment env — move to a Secret in production.

## Rollback Procedure
```bash
oc rollout undo deployment/coolstore
oc logs deployment/coolstore
```
The original EAP deployment path (README) remains intact — no source code was modified by this phase (only the stale pom TODO comment removed in favor of the Containerfile pipeline).

## Known Issues and Limitations
- **No container runtime in this environment** — image build, `podman run` smoke test and `grype` scan could not be executed; Dockerfile/CLI config were validated by Maven build + WildFly 26.1 CLI semantics review. Run the compose stack to verify.
- **HA clustering parity** — container runs `standalone-full.xml` (JMS included, no JGroups HA). Two replicas still receive all `topic/orders` messages (topic semantics), but EAP HA session replication is not configured; JGroups KUBE_PING is a follow-up if the EAP topology must be preserved exactly. The Quarkus migration (TG4) supersedes this.
- **Keycloak admin password** is a Deployment env placeholder — externalize to a Secret for production.
- **Secrets flagged by rgctl** live in vendored bower JS, not app code; tracked in migration plan TG1.5.

## Next Steps
1. ✅ Containerization of the current Java EE monolith complete
2. ⏳ Build + scan + smoke-test the image where podman is available (compose stack above)
3. ⏳ Execute Quarkus migration (plan TG2–TG5), then re-base the image on `ubi9/openjdk-21` with the Quarkus fast-jar (single-stage runtime, no app server) — replaces WildFly entirely
4. ⏳ Deploy to dev/test OpenShift; cutover per plan TG7
