# Containerization Report: CoolStore Quarkus

**Application**: CoolStore — Java EE 7 Monolith migrated to Quarkus 3.x  
**Report Date**: 2026-11-16  
**Branch**: `konveyor/migration-1794838418`  
**Author**: MigIQ Containerization Agent

---

## Executive Summary

| Item | Value |
|------|-------|
| Application | CoolStore REST back-end (catalog, cart, orders, shipping) |
| Language / Framework | Java 17 / Quarkus 3.6.4 (fast-jar) |
| Base Image (JVM) | `registry.access.redhat.com/ubi9/openjdk-17-runtime:latest` |
| Base Image (Native) | `registry.access.redhat.com/ubi9/ubi-micro:latest` |
| Deployment Target | OpenShift 4.x (restricted-v2 SCC) |
| Build Strategy | Multi-stage (builder → runtime) |
| Security Posture | Non-root UID 185, read-only root FS, all capabilities dropped |
| External Dependencies | PostgreSQL, AMQ Artemis, Keycloak OIDC |
| Local Dev Environment | podman-compose (all dependencies included) |

---

## 1. Graph Analysis Findings

### 1.1 Application Structure

The Quarkus application produced by the mig-execute stage consists of:

| Layer | Files | Key Classes |
|-------|-------|-------------|
| Model / Persistence | 8 | `CatalogItemEntity`, `InventoryEntity`, `Order`, `OrderItem` (Panache), `Product`, `Promotion`, `ShoppingCart`, `ShoppingCartItem` |
| Service | 9 | `CatalogService`, `OrderService`, `ProductService`, `PromoService`, `ShippingService`, `ShoppingCartService`, `ShoppingCartOrderProcessor`, `OrderServiceConsumer`, `InventoryNotificationConsumer` |
| REST endpoints | 4 | `RestApplication`, `ProductEndpoint`, `OrderEndpoint`, `CartEndpoint` |
| Utilities | 3 | `AuditService`, `StartupListener`, `Transformers` |
| **Total** | **24** | — |

### 1.2 Detected External Dependencies

| Dependency | Protocol / Port | Containerization Decision |
|------------|----------------|---------------------------|
| PostgreSQL 14 | JDBC / 5432 | CrunchyData PGO `PostgresCluster` CR in production; `rhel9/postgresql-15` in podman-compose |
| AMQ Artemis (AMQP 1.0) | AMQP / 5672 | AMQ Broker Operator CR (`activemqartemis-cr.yaml`) in production; `activemq-artemis-broker` in podman-compose |
| Keycloak (OIDC) | HTTPS / 443 | Keycloak Operator CR in production; `quay.io/keycloak/keycloak:23.0` with realm import in podman-compose |

### 1.3 Platform-Specific Code Identified and Remediated

| Item | Original | Remediation |
|------|----------|-------------|
| WebLogic `ApplicationLifecycleListener` | `weblogic.application.*` | Quarkus `@Observes StartupEvent / ShutdownEvent` |
| Proprietary audit JAR | `com.enterprise:audit-logging-library` | Custom `AuditService.java` writing to `audit.log.directory` env-driven path |
| JMS 2.0 / `@MessageDriven` | `javax.jms.*`, WildFly embedded broker | SmallRye Reactive Messaging `@Incoming` / `Emitter<String>` over AMQP |
| Hardcoded DB host | `localhost:5432` | `${DB_URL}` environment variable |
| Hardcoded AMQP host | WildFly T3 JNDI | `${AMQP_HOST}` / `${AMQP_PORT}` environment variables |
| Keycloak `keycloak.json` adapter | File-based adapter config | `quarkus.oidc.*` properties from env vars |
| Audit log directory | `./device-inventory-audit-logs` | `${AUDIT_LOG_DIR}` env var; PVC-backed mount in OpenShift |
| Taint flows (quantity param) | Unvalidated `int` path params | `@Min(1)` Bean Validation; invalid input → HTTP 400 |
| `system`-scoped Maven dep | `audit-logging-library-*.jar` in `lib/` | Removed; not present in Quarkus `pom.xml` |

### 1.4 Migration Complexity

**Rating: Medium**

Reasoning:
- Core business logic carried forward with minimal change
- Three external services (PostgreSQL, AMQ, Keycloak) require operator-managed infrastructure on OpenShift
- `@SessionScoped` shopping cart requires sticky sessions on the Route (already configured with `haproxy.router.openshift.io/balance: source`)
- Audit log file writes require a PersistentVolumeClaim — a simple, well-understood pattern
- No Windows-specific code, no native binaries, no hardcoded OS paths that survived migration

---

## 2. Base Image Selection

### JVM Build

| Attribute | Value |
|-----------|-------|
| **Build stage** | `registry.access.redhat.com/ubi9/openjdk-17:latest` |
| **Runtime stage** | `registry.access.redhat.com/ubi9/openjdk-17-runtime:latest` |
| **Reason** | Quarkus 3.6.4 requires Java 17; UBI9 images are RHEL 9–based, regularly patched, and support the OpenShift restricted-v2 SCC |
| **Default user** | UID 185 (defined by the UBI openjdk-17-runtime image) |
| **Curl added** | Yes — single `microdnf install -y curl && microdnf clean all` layer for `HEALTHCHECK` |

### Native Build

| Attribute | Value |
|-----------|-------|
| **Build stage** | `registry.access.redhat.com/ubi9/openjdk-17:latest` (GraalVM via Quarkus native plugin) |
| **Runtime stage** | `registry.access.redhat.com/ubi9/ubi-micro:latest` |
| **Reason** | ubi-micro has the smallest attack surface for a static native binary; no JVM required at runtime |
| **Default user** | UID 1001 |

---

## 3. Build Strategy

### Multi-Stage Build (JVM)

```
Stage 1 — Builder (openjdk-17)
  ├── COPY pom.xml → mvn dependency:go-offline (layer cache)
  ├── COPY src/
  └── RUN mvn package -DskipTests → target/quarkus-app/

Stage 2 — Runtime (openjdk-17-runtime)
  ├── RUN microdnf install curl (single layer, cache cleaned immediately)
  ├── COPY --chown=185 lib/ app/ quarkus/ *.jar from Stage 1
  ├── RUN mkdir /deployments/device-inventory-audit-logs (chown 185)
  ├── EXPOSE 8080 8443
  ├── USER 185
  ├── HEALTHCHECK /q/health/live
  └── ENTRYPOINT java -jar /deployments/quarkus-run.jar
```

### Key Build Optimisations

- **Dependency layer caching**: `pom.xml` copied and `mvn dependency:go-offline` run before source copy. Rebuilds that only change Java source skip the dependency download layer.
- **Quarkus fast-jar**: four-directory layout (`lib/`, `app/`, `quarkus/`, `*.jar`) means only the `app/` layer changes on most rebuilds.
- **Multi-stage separation**: build tools (Maven, JDK compiler) are absent from the final runtime image, reducing image size and attack surface.

---

## 4. Security Hardening

### Containerfile Hardening

| Control | Applied |
|---------|---------|
| Non-root user (UID 185 / 1001) | ✅ `USER 185` in final stage |
| No secrets in image layers | ✅ All credentials injected at runtime via env vars |
| Minimal packages | ✅ Only `curl` added to runtime image (for health check) |
| Layer size minimised | ✅ `microdnf clean all && rm -rf /var/cache/yum` in same RUN layer |
| `.dockerignore` | ✅ Excludes `.git`, `target/`, test sources, `*.env`, `*.pem`, `*.key` |
| `HEALTHCHECK` directive | ✅ `/q/health/live` with 30 s interval, 5 s timeout, 60 s start-period |
| Container-aware JVM flags | ✅ `UseContainerSupport`, `MaxRAMPercentage=75.0` |

### OpenShift Deployment Hardening

| Control | Applied |
|---------|---------|
| `runAsNonRoot: true` (pod) | ✅ `spec.securityContext.runAsNonRoot: true` |
| `seccompProfile: RuntimeDefault` (pod) | ✅ |
| `allowPrivilegeEscalation: false` (container) | ✅ |
| `capabilities.drop: [ALL]` (container) | ✅ |
| `readOnlyRootFilesystem: true` (container) | ✅ |
| Writable `/tmp` via emptyDir | ✅ `volumes[tmp]: emptyDir: {}` |
| Writable audit dir via PVC | ✅ `volumes[audit-logs]: persistentVolumeClaim` |
| Resource requests and limits | ✅ CPU 250m/1000m, Memory 256Mi/512Mi |
| TLS termination on Route | ✅ `tls.termination: edge`, `insecureEdgeTerminationPolicy: Redirect` |
| Sticky sessions for `@SessionScoped` | ✅ `haproxy.router.openshift.io/balance: source` |

### Secrets Management

All credentials are stored in `deploy/openshift/secret.yaml` as `stringData` placeholders. **No plaintext secrets are committed to Git.** Production workflow:

```bash
# Option A — oc CLI
oc create secret generic coolstore-quarkus-secret \
  --from-literal=DB_URL="jdbc:postgresql://..." \
  --from-literal=DB_PASSWORD="..." \
  ...

# Option B — External Secrets Operator (recommended for production)
# See docs/adr/ADR-004-secret-rotation-strategy.md
```

---

## 5. Generated Artefacts

### Container Artefacts

| File | Description |
|------|-------------|
| `coolstore-quarkus/Containerfile.jvm` | Multi-stage JVM build; hardened; `HEALTHCHECK` included |
| `coolstore-quarkus/Containerfile.native` | Multi-stage native binary build; ubi-micro runtime |
| `coolstore-quarkus/.dockerignore` | Excludes `.git`, test sources, secrets, build output |

### OpenShift Manifests (`deploy/openshift/`)

| File | Description |
|------|-------------|
| `namespace.yaml` | `coolstore-dev`, `coolstore-staging`, `coolstore-prod` namespaces |
| `configmap.yaml` | Non-sensitive runtime configuration |
| `secret.yaml` | Credential placeholders (must be replaced before deploy) |
| `pvc.yaml` | 5 Gi `gp3-csi` PVC for audit log persistence |
| `deployment.yaml` | 2-replica Deployment with full security context hardening |
| `service.yaml` | ClusterIP Service on port 8080 |
| `route.yaml` | Edge-TLS Route with sticky-session annotation |
| `hpa.yaml` | HPA — min 2 / max 10 replicas, CPU 70% / Memory 80% |
| `postgresql.yaml` | CrunchyData PGO `PostgresCluster` CR |
| `keycloak.yaml` | Keycloak Operator CR + `KeycloakRealmImport` |
| `kustomization.yaml` | Kustomize manifest aggregator |
| `argocd-application.yaml` | ArgoCD `Application` CR (automated prune + selfHeal) |

### AMQ Broker Manifests (`deploy/amq/`)

| File | Description |
|------|-------------|
| `activemqartemis-cr.yaml` | `ActiveMQArtemis` Operator CR — `topic/orders` multicast + DLQ |
| `broker-config.md` | Broker topology documentation and runbook |

### CI/CD (`deploy/pipelines/`)

| File | Description |
|------|-------------|
| `pipeline.yaml` | 5-stage Tekton Pipeline: clone → build → image-build → scan → deploy |
| `pipeline-run.yaml` | Sample `PipelineRun` |

### Local Development

| File | Description |
|------|-------------|
| `podman-compose.yml` | Full local stack: app + PostgreSQL 15 + AMQ Artemis + Keycloak 23 |

### Documentation

| File | Description |
|------|-------------|
| `docs/containerization-report.md` | This report |
| `docs/operator-runbook.md` | Day-2 operations guide |
| `docs/adr/ADR-001` → `ADR-004` | Architecture Decision Records |
| `docs/model-layer-migration.md` | Entity relationship documentation |
| `docs/taint-flow-remediation.md` | Taint flow remediation details |

---

## 6. Environment Variables Reference

| Variable | Description | Source | Required |
|----------|-------------|--------|----------|
| `DB_URL` | PostgreSQL JDBC URL | Secret | Yes (prod) |
| `DB_USERNAME` | Database user | Secret | Yes (prod) |
| `DB_PASSWORD` | Database password | Secret | Yes (prod) |
| `AMQP_HOST` | AMQ Artemis hostname | Secret | Yes |
| `AMQP_PORT` | AMQ Artemis AMQP port (default 5672) | Secret | Yes |
| `AMQP_USERNAME` | Broker username | Secret | Yes |
| `AMQP_PASSWORD` | Broker password | Secret | Yes |
| `OIDC_AUTH_SERVER_URL` | Keycloak realm URL | Secret | Yes (prod) |
| `OIDC_CLIENT_ID` | OIDC client ID | Secret | Yes (prod) |
| `OIDC_CLIENT_SECRET` | OIDC client secret | Secret | Yes (prod) |
| `SESSION_ENCRYPTION_KEY` | HTTP session encryption key (≥32 chars) | Secret | Yes (prod) |
| `AUDIT_LOG_DIR` | Directory for audit log files | ConfigMap | No (default: `./device-inventory-audit-logs`) |
| `QUARKUS_PROFILE` | Active Quarkus profile (`dev` / `prod`) | ConfigMap | No (default: `prod` in container) |
| `JAVA_OPTS` | JVM flags | Containerfile default | No |

---

## 7. Resource Requirements

### Application Pod

| Resource | Request | Limit |
|----------|---------|-------|
| CPU | 250m | 1000m |
| Memory | 256 Mi | 512 Mi |

### HPA Scaling Policy

| Parameter | Value |
|-----------|-------|
| Min replicas | 2 |
| Max replicas | 10 |
| CPU target | 70% |
| Memory target | 80% |

### PostgreSQL (CrunchyData PGO)

| Resource | Value |
|----------|-------|
| Instances | 1 (expand for HA) |
| Storage | 10 Gi (`ReadWriteOnce`) |
| Backup storage | 10 Gi (`ReadWriteOnce`) |

### Audit Log PVC

| Attribute | Value |
|-----------|-------|
| Size | 5 Gi |
| Access mode | `ReadWriteOnce` |
| Storage class | `gp3-csi` |

---

## 8. Health and Monitoring

### Health Endpoints (SmallRye Health)

| Endpoint | Purpose | Used By |
|----------|---------|---------|
| `/q/health/live` | Liveness — app process alive | Kubernetes `livenessProbe` |
| `/q/health/ready` | Readiness — dependencies connected | Kubernetes `readinessProbe` |
| `/q/health` | Combined | Ops/debugging |

### Probe Configuration

| Probe | Initial Delay | Period | Timeout | Fail Threshold |
|-------|--------------|--------|---------|----------------|
| Liveness | 30 s | 10 s | 5 s | 3 |
| Readiness | 15 s | 5 s | 5 s | 3 |

### Metrics (Micrometer + Prometheus)

- Endpoint: `/q/metrics`
- Scrape interval: configure in Prometheus `ServiceMonitor` or via OpenShift user-workload monitoring
- Key metrics: JVM heap, GC pause, HTTP request latency, AMQP consumer lag

---

## 9. Local Development

### Prerequisites

```bash
# Install podman and podman-compose
dnf install -y podman python3-podman-compose   # RHEL/Fedora
# or
brew install podman podman-compose              # macOS

# (Optional) Pull images ahead of time
podman pull registry.redhat.io/rhel9/postgresql-15:latest
podman pull quay.io/artemiscloud/activemq-artemis-broker:latest
podman pull quay.io/keycloak/keycloak:23.0
```

### Start the Stack

```bash
# 1. Build the application image
cd coolstore-quarkus
podman build -f Containerfile.jvm -t coolstore-quarkus:dev .

# 2. Start all services (from repo root)
cd ..
podman-compose up -d

# 3. Wait for services to become healthy
podman-compose ps

# 4. Test the application
curl http://localhost:8080/q/health
curl http://localhost:8080/services/products

# 5. View logs
podman-compose logs -f app
```

### Stop and Clean Up

```bash
podman-compose down           # stop containers, keep volumes
podman-compose down -v        # stop containers and wipe all data volumes
```

### Service Endpoints (Local)

| Service | URL | Credentials |
|---------|-----|-------------|
| Application | http://localhost:8080 | — |
| Health | http://localhost:8080/q/health | — |
| Swagger UI | http://localhost:8080/q/swagger-ui | — |
| Metrics | http://localhost:8080/q/metrics | — |
| Keycloak admin | http://localhost:8180 | admin / admin |
| Artemis console | http://localhost:8161 | admin / admin |
| PostgreSQL | localhost:5432 | coolstore / coolstore-dev-password |

---

## 10. Deployment Instructions

### Prerequisites

```bash
# OpenShift CLI configured and logged in
oc whoami

# Required operators installed (by cluster admin):
#   - Red Hat Integration - AMQ Broker Operator
#   - Keycloak Operator
#   - CrunchyData PostgreSQL Operator (PGO)
#   - OpenShift GitOps (ArgoCD)
#   - OpenShift Pipelines (Tekton)
```

### Manual Deployment Steps

```bash
# 1. Create namespaces
oc apply -f deploy/openshift/namespace.yaml

# 2. Populate secrets (replace placeholders with real values)
#    Use Vault / ESO in production — see ADR-004
oc apply -f deploy/openshift/secret.yaml   # review and edit first!

# 3. Apply ConfigMap
oc apply -f deploy/openshift/configmap.yaml

# 4. Create storage
oc apply -f deploy/openshift/pvc.yaml

# 5. Deploy infrastructure operators
oc apply -f deploy/openshift/postgresql.yaml
oc apply -f deploy/amq/activemqartemis-cr.yaml
oc apply -f deploy/openshift/keycloak.yaml

# Wait for infrastructure to be ready
oc wait --for=condition=Ready PostgresCluster/coolstore-db -n coolstore-dev --timeout=5m
oc wait --for=condition=Ready ActiveMQArtemis/coolstore-broker -n coolstore-dev --timeout=5m

# 6. Build and push image
cd coolstore-quarkus
podman build -f Containerfile.jvm -t quay.io/coolstore/coolstore-quarkus:latest .
podman push quay.io/coolstore/coolstore-quarkus:latest

# 7. Deploy application
oc apply -f deploy/openshift/deployment.yaml
oc apply -f deploy/openshift/service.yaml
oc apply -f deploy/openshift/route.yaml
oc apply -f deploy/openshift/hpa.yaml

# 8. Verify
oc get pods -n coolstore-dev
oc get route coolstore-quarkus -n coolstore-dev
curl https://$(oc get route coolstore-quarkus -n coolstore-dev -o jsonpath='{.spec.host}')/q/health
```

### GitOps Deployment (ArgoCD)

```bash
# Install the ArgoCD Application CR — ArgoCD will sync all manifests automatically
oc apply -f deploy/openshift/argocd-application.yaml

# Monitor sync status
oc get application coolstore-quarkus -n openshift-gitops
```

### Tekton Pipeline Deployment

```bash
# Install pipeline
oc apply -f deploy/pipelines/pipeline.yaml

# Trigger a pipeline run
oc apply -f deploy/pipelines/pipeline-run.yaml

# Watch progress
tkn pipelinerun logs --last -f
```

---

## 11. Rollback Procedure

```bash
# View rollout history
oc rollout history deployment/coolstore-quarkus -n coolstore-dev

# Rollback to previous revision
oc rollout undo deployment/coolstore-quarkus -n coolstore-dev

# Rollback to specific revision
oc rollout undo deployment/coolstore-quarkus -n coolstore-dev --to-revision=2

# Verify
oc get pods -n coolstore-dev
oc logs deployment/coolstore-quarkus -n coolstore-dev
```

---

## 12. Known Issues and Limitations

| Issue | Impact | Mitigation |
|-------|--------|-----------|
| `ReadWriteOnce` PVC for audit logs | Audit log PVC is bound to one node; rolling updates work because `maxUnavailable: 0` ensures the old pod terminates before the new one starts | Switch to `ReadWriteMany` (CephFS) or object storage if multi-node concurrency is needed |
| Session stickiness required | `@SessionScoped` shopping cart lives in-memory; requests must be routed to the same pod | `haproxy.router.openshift.io/balance: source` on the Route; consider externalising session state to Redis for future HA improvements |
| Keycloak realm import — first-run only | `KeycloakRealmImport` applies once; subsequent realm changes must be applied via the Keycloak admin console or re-import | Document realm change management procedure in operator runbook |
| Native build requires GraalVM | `Containerfile.native` builds a GraalVM native image; Tekton pipeline uses JVM build by default | Add a separate native pipeline stage or use `quarkus.native.container-build=true` to offload to a GraalVM container |

---

## 13. Security Considerations

### Image Security

- Base images are official Red Hat UBI 9 images, regularly patched and covered by Red Hat security advisories
- Multi-stage build ensures build tools are absent from the final runtime image
- No secrets are baked into image layers; all credentials injected at runtime

### Runtime Security

| Control | Configuration |
|---------|---------------|
| Non-root execution | UID 185 (JVM) / 1001 (native); `runAsNonRoot: true` |
| Capability restriction | `capabilities.drop: [ALL]` |
| Privilege escalation blocked | `allowPrivilegeEscalation: false` |
| Root filesystem | Read-only (`readOnlyRootFilesystem: true`); writable paths via emptyDir and PVC |
| Seccomp | `RuntimeDefault` profile |
| SCC compatibility | `restricted-v2` (default OpenShift SCC) |

### Network Security

- External traffic: TLS edge termination; HTTP → HTTPS redirect enforced by Route
- Internal traffic: unencrypted within the cluster (acceptable for intra-namespace microservices)
- Recommend adding OpenShift `NetworkPolicy` resources to restrict cross-namespace traffic

### Secrets Hygiene

- No plaintext credentials in Git
- All sensitive values in `deploy/openshift/secret.yaml` are clearly marked as `REPLACE_WITH_ACTUAL_*`
- Recommend integrating with HashiCorp Vault or the External Secrets Operator (see ADR-004)

---

## 14. Next Steps

| Step | Priority | Owner |
|------|----------|-------|
| Replace secret placeholders with real credentials (Vault / ESO) | 🔴 High | Platform team |
| Build and push image to Quay.io | 🔴 High | Dev team |
| Deploy to `coolstore-dev` and validate `/q/health/ready` | 🔴 High | Dev team |
| Run Tekton pipeline end-to-end | 🟡 Medium | Platform team |
| Configure Prometheus `ServiceMonitor` for metrics scraping | 🟡 Medium | Platform team |
| Add `NetworkPolicy` resources for cross-namespace isolation | 🟡 Medium | Platform team |
| Conduct OWASP ZAP scan on deployed endpoints | 🟡 Medium | Security team |
| Performance baseline with Gatling / k6 | 🟡 Medium | Dev team |
| 48-hour parallel run in `coolstore-staging` | 🟢 Story 8 | All teams |
| Canary cutover: 10% → 50% → 100% | 🟢 Story 8 | Platform team |
| Decommission monolith after 30-day stability window | 🟢 Story 8 | All teams |

---

## References

- [Red Hat UBI Image Catalogue](https://catalog.redhat.com/software/containers/search)
- [Quarkus Container Images Guide](https://quarkus.io/guides/container-image)
- [OpenShift Security Context Constraints](https://docs.openshift.com/container-platform/latest/authentication/managing-security-context-constraints.html)
- [AMQ Broker Operator](https://access.redhat.com/documentation/en-us/red_hat_amq_broker)
- [CrunchyData PGO](https://access.crunchydata.com/documentation/postgres-operator/)
- [Keycloak Operator](https://www.keycloak.org/operator/installation)
- [ADR-001: Quarkus over Spring Boot](adr/ADR-001-quarkus-over-spring-boot.md)
- [ADR-004: Secret Rotation Strategy](adr/ADR-004-secret-rotation-strategy.md)
- [Operator Runbook](operator-runbook.md)
