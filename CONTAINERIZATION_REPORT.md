# Containerization Report: CoolStore (Quarkus 3 monolith)

## Executive Summary

- **Application**: Red Hat CoolStore e-commerce storefront — migrated from Java EE 7 / JBoss EAP 7.4 WAR to **Quarkus 3.15.1 / Java 21** fast-jar (see `mig-execute-workspace/EXECUTION_REPORT.md`)
- **Language/Framework**: Java 21, Quarkus 3 (REST, Hibernate ORM, Flyway, Artemis JMS, OIDC, Micrometer, smallrye-health) + AngularJS 1.x static frontend; Maven build
- **Base Images**: `registry.access.redhat.com/ubi9/openjdk-21` (builder) → `registry.access.redhat.com/ubi9/openjdk-21-runtime` (runtime)
- **Deployment Target**: Red Hat OpenShift (namespace `coolstore`)
- **Build Time / Image Size / Security Scan**: not measurable in this stage environment (no container runtime available — see Known Issues); CI performs the build via the same `Dockerfile`
- **Migration Complexity**: **Low** — config fully externalized, no filesystem writes, no platform-specific code; one frontend-config caveat (below)

## Graph Analysis Findings (rgctl)

Index (re-)discovered with `rgctl discover . --with-cfg --with-security --with-taint --with-harmonic`:

- **Nodes/Edges**: 36,736 nodes / 127,746 edges across 1,289 files (bulk is vendored `META-INF/resources/bower_components` frontend assets)
- **Communities**: 24,537 detected (modularity 0.46); application-level communities map to `model`, `service`, `rest`, `utils` packages — a single deployable monolith, **one container** (no microservice split warranted at ~1.8K LOC Java / 23 source files)
- **App → external `uses` edges** (215 total): Jakarta Persistence/JMS/REST, Quarkus runtime (`StartupEvent`, health, tests), JBoss Logging, MicroProfile Config, Jackson, and `com.enterprise.audit.logging.*` (internal library 2.0.0)
- **Taint flows**: 1 vulnerable — vendored `bower_components/matchHeight/gulpfile.js` `shell()` (build tooling, not application code, not shipped logic)
- **Security scan**: 11 potential secrets — all traced to `realm-export.json` (Keycloak realm fixture) and vendored assets; **no credentials in application code or config** (verified: `application.properties` uses `${ENV:default}` exclusively)

### External Dependencies → Deployment Decisions

| Dependency | Evidence | Containerization decision |
|---|---|---|
| PostgreSQL 16 | `quarkus-jdbc-postgresql`, Flyway `V1_1/V1_2` | StatefulSet + PVC (1 Gi, RWO), Service `postgresql:5432` |
| ActiveMQ Artemis | `quarkiverse:quarkus-artemis-jms:3.5.1`, topic `topic/orders` | Broker Deployment + PVC journal, Service `amq-broker:61616` |
| Keycloak 26 (OIDC) | `quarkus-oidc`, realm `eap`, client `eap-app` | Deployment + Route, realm imported from `realm-export.json` |
| audit-logging-library 2.0.0 (internal) | `StreamableAuditLogger` TCP (`OrderService`) | Config-only (`AUDIT_LOGGER_TCP_HOST/PORT`); collector not deployed — see Known Issues |

### Platform-Specific Code / Blockers Identified

- **No hardcoded paths** in Java sources; **no filesystem writes** (audit logging is TCP-based; logs go to stdout as JSON) → read-only root filesystem is safe
- **No localhost assumptions in app code**; `application.properties` defaults (`localhost`) are env-var overridable and are overridden by `coolstore-config` ConfigMap
- **Blocker (documented, not fixed)**: `src/main/resources/META-INF/resources/keycloak.json` hardcodes `auth-server-url: http://localhost:8081/` — the browser-side OIDC adapter config is baked into the image. Must be updated to the public Keycloak Route URL (and the realm's redirect URIs) before enabling SSO on-cluster — matches open task 7.2/7.3 from the execution stage
- **Secrets hygiene**: all runtime credentials injected from OpenShift Secrets; only `CHANGE_ME` placeholders committed

## Containerization Strategy

- **Selected images**: `ubi9/openjdk-21` (builder — ships Maven 3.9 + JDK 21, keeps the supply chain Red-Hat-only) and `ubi9/openjdk-21-runtime` (runtime — JRE only, smaller attack surface). Rationale: language-specific UBI images per skill guidance; alternatives (`ubi9/ubi-minimal`, `maven:3.9-eclipse-temurin-21` used by the previous interim Containerfile) rejected in favor of a fully Red Hat chain
- **Build**: multi-stage; dependency layer cached (`pom.xml` + internal jar first); internal `com.enterprise:audit-logging-library:2.0.0` installed into the builder's local Maven repo (not on Central); tests skipped in image builds (`-Dmaven.test.skip=true`, they run in CI via `mvn verify`)
- **Artifact**: Quarkus fast-jar (`target/quarkus-app/`) copied as uid 185

### Security Hardening Applied

- ✅ Multi-stage build; build tools absent from runtime image
- ✅ Non-root execution (image `USER 185`; manifests `runAsNonRoot` — no `runAsUser` pinned so OpenShift `restricted-v2` SCC may allocate namespace uids)
- ✅ `readOnlyRootFilesystem: true` + `emptyDir` `/tmp`; all capabilities dropped; `allowPrivilegeEscalation: false`; `seccompProfile: RuntimeDefault`
- ✅ `.dockerignore` excludes `.git`, docs, tests, manifests, workspaces from the build context
- ✅ Secrets/ConfigMap externalized; no credentials in image layers
- ✅ Edge-TLS Routes with HTTPS redirect; probes on Quarkus `/q/health/*`
- ⏳ Vulnerability scan (grype/trivy) not runnable locally — add to CI after first image build (command in Deployment Instructions)

## Generated Artifacts

| File | Purpose |
|---|---|
| `Dockerfile` | Multi-stage UBI build (replaces interim `Containerfile`) |
| `.dockerignore` | Minimal build context |
| `configmap.yaml` | Non-sensitive app config (`coolstore-config`) |
| `secret.yaml` | All credentials (app db/broker, PostgreSQL, AMQ, Keycloak admin) — `CHANGE_ME` placeholders |
| `database-statefulset.yaml` | PostgreSQL 16 + Service + PVC (volumeClaimTemplate) |
| `amq-broker.yaml` | Artemis broker + PVC + Service |
| `keycloak.yaml` | Keycloak 26 + Service + Route (realm import) |
| `deployment.yaml` | Application workload (2 replicas, rolling update `maxUnavailable: 0`) |
| `service.yaml` / `route.yaml` | Internal / external access |
| `hpa.yaml` | Autoscaling 2–5 pods @ 80% CPU |
| `podman-compose.yml` | Local dev environment (app + all backing services) |

The interim `openshift/` manifests and `Containerfile` from the execute stage were **removed** — the root files above are the single source of truth; CI and docs were updated accordingly.

## Deployment Instructions

Prerequisites: `oc` CLI logged in; `oc new-project coolstore`.

```bash
# 0. Replace CHANGE_ME values in secret.yaml (or create secrets with oc)
# 1. Keycloak realm ConfigMap (prerequisite for keycloak.yaml)
oc create configmap keycloak-realm --from-file=realm-export.json=realm-export.json
# 2. Apply in dependency order
oc apply -f configmap.yaml -f secret.yaml
oc apply -f database-statefulset.yaml -f amq-broker.yaml -f keycloak.yaml
oc apply -f deployment.yaml -f service.yaml -f route.yaml -f hpa.yaml
# 3. Build, tag and push the image, then set it
podman build -f Dockerfile -t quay.io/<ns>/coolstore-quarkus:latest .
podman push  quay.io/<ns>/coolstore-quarkus:latest
oc set image deployment/coolstore-quarkus coolstore-quarkus=quay.io/<ns>/coolstore-quarkus:latest
# 4. Verify
oc get pods && oc get route coolstore
curl -f https://$(oc get route coolstore -o jsonpath='{.spec.host}')/services/products
# 5. (Post-build) image vulnerability scan
grype quay.io/<ns>/coolstore-quarkus:latest --fail-on high
```

CI (`.github/workflows/build-openshift.yml`) does the same on pushes to `main`: `mvn verify` → `docker build -f Dockerfile` → push to Quay → apply root manifests → `oc set image`.

## Configuration Reference

| Variable | Description | Source | Required |
|---|---|---|---|
| `QUARKUS_DATASOURCE_URL` | PostgreSQL JDBC URL | ConfigMap | Yes |
| `QUARKUS_DATASOURCE_USERNAME` / `PASSWORD` | DB credentials | Secret `coolstore-db` | Yes |
| `QUARKUS_ARTEMIS_URL` | Artemis broker URL | ConfigMap | Yes |
| `QUARKUS_ARTEMIS_USERNAME` / `PASSWORD` | Broker credentials | Secret `coolstore-broker` | Yes |
| `QUARKUS_OIDC_AUTH_SERVER_URL` | Keycloak realm URL | ConfigMap | Yes |
| `AUDIT_LOGGER_TCP_HOST` / `PORT` | Audit collector TCP endpoint | ConfigMap | No (events dropped if absent) |

**Resources** — app: 250m/512Mi requests, 1 CPU/1Gi limits; PostgreSQL & AMQ: 100m/256Mi requests, 1Gi limits, 1 Gi PVCs; Keycloak: 250m/512Mi requests, 1Gi limits.

**Health** — readiness `/q/health/ready`, liveness `/q/health/live`, startup `/q/health/started` (150 s budget); metrics at `/q/metrics` (Micrometer, scrape annotations on pods).

## Rollback Procedure

```bash
oc rollout history deployment/coolstore-quarkus
oc rollout undo deployment/coolstore-quarkus            # or --to-revision=N
```

Legacy EAP WAR remains in git history; see `docs/operations-runbook.md`.

## Known Issues and Limitations

1. **No container runtime / JDK / Maven in this stage environment** — the Dockerfile and manifests are validated as artifacts (YAML parsed, env wiring cross-checked against `application.properties`); the actual image build + smoke test must run in CI or a developer machine (first CI run on `main` will exercise `docker build -f Dockerfile`).
2. **Frontend OIDC config** (`keycloak.json` → `http://localhost:8081/`) must be repointed at the Keycloak Route URL before SSO works on-cluster (open tasks 7.2/7.3 from execution stage). Catalog browsing works without it.
3. **Audit collector** (`audit-collector:1514`) is referenced but not deployed; `StreamableAuditLogger` tolerates the absent endpoint (test suite boots without one) and audit events are dropped until a collector (e.g. syslog/edge TCP collector) is provisioned.
4. **In-memory cart state** (`ConcurrentHashMap` keyed by cartId) is per-pod; carts are lost on pod restart and not shared across replicas — acceptable per migrated design, documented for the deploy stage.
5. Base image tags use `:latest` (consistent with CI/Jib config) — pin digest/version tags for production promotion.

## Next Steps

1. ✅ Containerization complete (this stage)
2. ⏳ Build image via CI, run `grype` scan, smoke test `/services/products` + checkout flow
3. ⏳ Fix `keycloak.json` / realm redirect URIs for on-cluster SSO (deploy stage tasks 7.2/7.3)
4. ⏳ Provision an audit collector endpoint
5. ⏳ Parallel run, performance baseline, production cutover (execute-stage open tasks 9.5–9.10)

## Support and Resources

- Red Hat UBI images: https://catalog.redhat.com/software/containers/search
- OpenShift docs: https://docs.openshift.com/
- Podman: https://docs.podman.io/
