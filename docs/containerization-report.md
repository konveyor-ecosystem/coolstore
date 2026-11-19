# Containerization Report: CoolStore Monolith

## Executive Summary

- **Application**: CoolStore monolith (Java EE 7 / JBoss EAP 7.4 → Quarkus 3.15.1 / Java 21)
- **Language/Framework**: Java 21, Quarkus 3.15.1 (fast-jar), Maven 3.9
- **Base Image**: `registry.access.redhat.com/ubi9/openjdk-21:1.20` (build) → `ubi9/openjdk-21-runtime:1.20` (runtime)
- **Deployment Target**: OpenShift (Red Hat UBI only, security-hardened)
- **Build Validation**: `mvn package` BUILD SUCCESS locally (fast-jar, 61 MB); image build pending podman availability (see Known Issues)
- **Security Scan**: Podman/grype unavailable in this environment — scanning deferred to the Tekton CI pipeline (Trivy) which runs on every image build

## Graph Analysis Findings

Index: `rgctl discover . --with-cfg --with-security --with-taint --with-harmonic`
(29079 nodes, 88237 edges, 15253 functions analyzed, modularity 0.74, 233 circular dependencies)

### Application Structure
- **Primary Language**: Java 21 (backend) + vendored AngularJS/static assets under `src/main/resources/META-INF/resources/bower_components/`
- **Framework**: Quarkus 3.15.1 — REST (JAX-RS), Hibernate ORM, JDBC PostgreSQL, Flyway, OIDC, SmallRye AMQP, SmallRye Health, Micrometer
- **Build system**: Maven 3.9 with a project-local repository (`local-maven-repo/`) vendoring `com.enterprise:audit-logging-library:2.0.0` (system scope is not container-friendly)
- **Artifact**: Quarkus fast-jar (`target/quarkus-app/`)
- **PageRank hotspots**: dominated by vendored frontend libraries (`isotope.js`, `d3.js`, `angular`) — the Java backend itself is a cohesive monolith with no god-node concerns for containerization

### External Dependencies
| Dependency | Detection | Container strategy |
|------------|-----------|-------------------|
| PostgreSQL (JDBC) | `quarkus-jdbc-postgresql`, `QUARKUS_DATASOURCE_*` env | StatefulSet with 10 Gi PVC (`openshift/postgresql.yaml`) |
| Keycloak (OIDC, realm `eap`) | `quarkus-oidc`, `KEYCLOAK_URL` env | Deployment + realm import (`openshift/keycloak.yaml`) |
| AMQP 1.0 broker (orders address) | `quarkus-messaging-amqp`, `AMQP_*` env | Artemis Deployment (`openshift/broker.yaml`) |
| Audit TCP stream (port 4560) | `StreamableAuditLogger` via `AUDIT_STREAM_*` env | External service (OpenShift ConfigMap points at `audit-collector`; dev sink in podman-compose) |

### Platform-Specific Code Identified
- **Hardcoded filesystem paths**: none — the legacy `FileSystemAuditLogger` (./device-inventory-audit-logs) was replaced by `StreamableAuditLogger` (TCP) during migration
- **localhost assumptions**: none in Java code — all endpoints externalized via env vars with localhost *dev defaults* (`AuditLoggerProducer` `@ConfigProperty` defaults); the only remaining `localhost` references are comments and overridable defaults in `application.properties`
- **Configuration**: single `src/main/resources/application.properties`, fully env-var driven — no hardcoded credentials required at build time
- **Secrets flagged by `--with-security` (5)**: all were dev-default/example credentials (datasource + AMQP dev defaults, Keycloak `admin/admin`, PostgreSQL + Artemis manifest literals) — all now externalized to `coolstore-secrets` Secret refs
- **Taint flows**: 1 flagged (source-tree noise in vendored JS); no Java taint-to-filesystem paths

### Migration Complexity: Low

**Reasoning**: single-module Maven monolith producing a self-contained fast-jar; zero filesystem writes; all external endpoints already externalized as env vars; supporting services (DB, IdP, broker) map 1:1 to standard OpenShift workloads.

## Containerization Strategy

### Base Image Selection
- **Selected (build)**: `registry.access.redhat.com/ubi9/openjdk-21:1.20` — official Red Hat UBI9 with OpenJDK 21 **and Maven** included, replacing the previous non-Red-Hat `maven:3.9-eclipse-temurin-21` builder (Red Hat UBI only policy)
- **Selected (runtime)**: `registry.access.redhat.com/ubi9/openjdk-21-runtime:1.20` — JRE-only variant, smaller attack surface; ships the `run-java.sh` launcher and honours `JAVA_OPTS_APPEND`
- **Alternatives considered**: `ubi9/ubi-minimal` + custom JDK install (more maintenance), Quarkus JIB (`mvn package -Popenshift` remains available as an alternative path)

### Build Strategy
- **Type**: Multi-stage
- **Stage 1**: UBI9 OpenJDK 21 (root) — `dependency:go-offline` (layer-cached deps, incl. vendored `local-maven-repo`), then `mvn -DskipTests package`
- **Stage 2**: UBI9 OpenJDK 21 **runtime** (non-root UID 185) — copies only `target/quarkus-app/`

### Security Hardening Applied
- ✅ Multi-stage build; build tools (Maven, JDK) excluded from the runtime image
- ✅ Non-root user (UID 185, UBI default; `runAsNonRoot` enforced in the Deployment)
- ✅ `.containerignore` excludes `.git`, tests, docs, workspaces, `target/`, manifests from the build context
- ✅ All secrets externalized (Secret refs; no credentials in image layers or manifests)
- ✅ `HEALTHCHECK` in Containerfile + readiness/liveness/startup probes in the Deployment
- ✅ Pinned image tags (`1.20`, not `latest`) for base images
- ✅ Runtime container: `readOnlyRootFilesystem: true`, all capabilities dropped, `allowPrivilegeEscalation: false`, `seccompProfile: RuntimeDefault`, writable `/tmp` via emptyDir
- ✅ Database: deprecated EOL `rhscl/postgresql-13-rhel7` replaced by `rhel9/postgresql-15`; ephemeral emptyDir replaced by 10 Gi PVC (StatefulSet)

## Generated Artifacts

### Container Image
- **Name**: `coolstore-monolith:latest` (local) / `image-registry.openshift-image-registry.svc:5000/coolstore/coolstore-monolith` (OpenShift)
- **Layers**: fast-jar copied as a single layer on top of the UBI runtime
- **Created**: 2026-11-19

### Files
| File | Purpose |
|------|---------|
| `Containerfile` | Multi-stage UBI9 OpenJDK 21 build + hardened runtime |
| `.containerignore` | Build-context exclusions (podman/docker) |
| `openshift/deployment.yaml` | App workload — 2 replicas, probes, resources, securityContext, read-only rootfs |
| `openshift/service.yaml` | ClusterIP Service (80 → 8080) |
| `openshift/route.yaml` | Edge-TLS Route |
| `openshift/configmap.yaml` | Non-sensitive config (service hostnames/ports) |
| `openshift/secret.example.yaml` | Template for `coolstore-secrets` (all credential keys) |
| `openshift/postgresql.yaml` | PostgreSQL 15 StatefulSet + headless + client Services + 10 Gi PVC |
| `openshift/keycloak.yaml` | Keycloak 26 + realm import (creds from Secret) |
| `openshift/broker.yaml` | Artemis AMQP broker (creds from Secret) |
| `openshift/hpa.yaml` | Autoscaling 2→6 pods @ 70% CPU |
| `openshift/smoke-test.yaml` | Post-deploy smoke Job (health + products endpoint) |
| `podman-compose.yml` | Local dev stack mirroring OpenShift (app + postgres + broker + keycloak + audit sink) |

## Deployment Instructions

### Prerequisites
1. OpenShift cluster access (`oc` CLI logged in)
2. Project created: `oc new-project coolstore`
3. Registry pull secret for `registry.redhat.io` (for `rhel9/postgresql-15`) linked to the namespace

### Steps to Deploy

```bash
# 1. Secrets (never commit real values)
oc apply -f openshift/secret.example.yaml   # after replacing CHANGE_ME values

# 2. Keycloak realm ConfigMap + supporting services
oc create configmap keycloak-realm --from-file=realm-export.json=realm-export.json
oc apply -f openshift/postgresql.yaml
oc apply -f openshift/keycloak.yaml
oc apply -f openshift/broker.yaml

# 3. Configuration + application workload
oc apply -f openshift/configmap.yaml
podman build -f Containerfile -t image-registry.openshift-image-registry.svc:5000/coolstore/coolstore-monolith:latest .
podman push image-registry.openshift-image-registry.svc:5000/coolstore/coolstore-monolith:latest
#    (alternative: mvn package -Popenshift builds/pushes via Jib)
oc apply -f openshift/deployment.yaml
oc apply -f openshift/service.yaml
oc apply -f openshift/route.yaml
oc apply -f openshift/hpa.yaml

# 4. Verify
oc get pods
oc logs deployment/coolstore-monolith
oc create -f openshift/smoke-test.yaml && oc logs job/coolstore-smoke-test
curl -f https://$(oc get route coolstore-monolith -o jsonpath='{.spec.host}')/q/health/ready
```

### Local Development

```bash
podman-compose up -d        # app on :8080, keycloak on :8081, postgres :5432, broker :5672
podman-compose down -v      # stop and reset data
```

## Configuration Reference

| Variable | Description | Source | Required |
|----------|-------------|--------|----------|
| `QUARKUS_DATASOURCE_HOST/PORT/DATABASE` | PostgreSQL location | ConfigMap | Yes |
| `QUARKUS_DATASOURCE_USERNAME/PASSWORD` | DB credentials | Secret | Yes |
| `KEYCLOAK_URL` | OIDC issuer base URL | ConfigMap | Yes |
| `AMQP_HOST/PORT` | Artemis broker location | ConfigMap | Yes |
| `AMQP_USERNAME/PASSWORD` | Broker credentials | Secret | Yes |
| `AUDIT_STREAM_HOST/PORT/PROTOCOL` | Audit TCP collector | ConfigMap | No (async, failures tolerated) |

## Health and Monitoring

- **Liveness**: `GET /q/health/live` (probe: 10s delay, 15s period)
- **Readiness**: `GET /q/health/ready` (probe: 10s delay, 10s period)
- **Startup**: `GET /q/health/started` (failureThreshold 30 × 2s)
- **Metrics**: `GET /q/metrics` (Micrometer/Prometheus)
- **Container HEALTHCHECK**: `curl -f /q/health/live` every 30s (standalone podman/docker)

## Resource Requirements

**Application pod**: CPU 250m→1, memory 512Mi→1Gi (2 replicas, HPA to 6 @ 70% CPU)
**PostgreSQL pod**: CPU 250m→1, memory 256Mi→1Gi, storage 10 Gi PVC

## Rollback Procedure

```bash
oc rollout history deployment/coolstore-monolith
oc rollout undo deployment/coolstore-monolith           # or --to-revision=N
oc get pods && oc logs deployment/coolstore-monolith
```
Schema is unchanged from the EAP era (Flyway `baseline-on-migrate`, baseline 1.2), so the legacy EAP WAR can be redeployed against the same PostgreSQL during the rollback window.

## Known Issues and Limitations

1. **Image build not executed here** — podman is not available in this environment. The Maven build stage was validated locally (`mvn -DskipTests package` → BUILD SUCCESS, fast-jar 61 MB) and YAML manifests were syntax-validated. The Tekton CI pipeline (`.tekton/pipeline.yaml`: buildah build + Trivy scan) will produce and scan the image on the cluster.
2. **Vulnerability scanning deferred** — grype/Trivy not installed locally; first image build in CI must be reviewed for criticals/highs before promotion.
3. **PostgreSQL major version bump** (13 EOL image → 15): schema is standard SQL and unchanged; validate a backup/restore rehearsal before cutover if migrating existing data.
4. **Keycloak admin creds in dev stack** — `podman-compose.yml` uses `admin/admin` for local convenience only; OpenShift credentials come from the Secret.
5. **Audit collector** — OpenShift expects an external `audit-collector` TCP service (not part of these manifests); the dev stack provides a discard-only socat sink.

## Next Steps

1. ✅ Containerization complete (UBI images, hardened manifests, compose stack, report)
2. ⏳ Build image in CI (Tekton) and review Trivy scan results
3. ⏳ Deploy to dev/test environment and run the smoke-test Job
4. ⏳ Point `AUDIT_STREAM_HOST` at a real audit collector service
5. ⏳ Performance testing under load; tune HPA/resources
6. ⏳ Production cutover per migration runbook

## Support and Resources

- **Red Hat UBI Images**: https://catalog.redhat.com/software/containers/search
- **OpenShift Documentation**: https://docs.openshift.com/
- **Podman Documentation**: https://docs.podman.io/
- **Quarkus container images**: https://quarkus.io/guides/container-image
