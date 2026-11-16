# Deployment Report: CoolStore Quarkus → OpenShift

**Application**: CoolStore — Quarkus 3.x (migrated from Java EE 7 / WebLogic monolith)
**Report Date**: 2026-11-16
**Branch**: `konveyor/migration-1794838418`
**Author**: MigIQ Deployment Agent
**Namespace**: `coolstore-dev`

---

## Executive Summary

| Item | Value |
|------|-------|
| Deployment target | OpenShift 4.x — namespace `coolstore-dev` |
| Application image | `quay.io/coolstore/coolstore-quarkus:latest` |
| Replicas | 2 (HPA min/max: 2–10) |
| GitOps tool | ArgoCD (OpenShift GitOps) |
| CI/CD pipeline | Tekton (OpenShift Pipelines) |
| External dependencies | CrunchyData PostgreSQL, AMQ Artemis Broker, Keycloak |
| Deployment method | Kustomize (applied by ArgoCD or `oc apply -k`) |
| Security posture | restricted-v2 SCC; NetworkPolicy default-deny + explicit allow |
| Observability | SmallRye Health probes + ServiceMonitor (Prometheus) |

---

## 1. Prerequisites

Before applying any manifests the following must be satisfied:

### 1.1 Cluster-side Operators (installed by cluster admin)

| Operator | Namespace | Purpose |
|----------|-----------|---------|
| Red Hat Integration – AMQ Broker | `openshift-operators` | Manages `ActiveMQArtemis` CRs |
| Keycloak Operator | `openshift-operators` | Manages `Keycloak` and `KeycloakRealmImport` CRs |
| CrunchyData PostgreSQL Operator (PGO) | `openshift-operators` | Manages `PostgresCluster` CRs |
| OpenShift GitOps (ArgoCD) | `openshift-gitops` | Applies Kustomize manifests from Git |
| OpenShift Pipelines (Tekton) | `openshift-pipelines` | Runs CI/CD pipeline |
| Prometheus / User-Workload Monitoring | `openshift-monitoring` | Scrapes `ServiceMonitor` |

### 1.2 Enable User-Workload Monitoring

```bash
oc patch configmap cluster-monitoring-config \
  -n openshift-monitoring \
  --type=merge \
  -p '{"data":{"config.yaml":"enableUserWorkload: true\n"}}'

# Label the namespace so Prometheus discovers it
oc label namespace coolstore-dev openshift.io/cluster-monitoring=true
```

### 1.3 Cluster Task availability (Tekton)

The pipeline references cluster-scoped Tekton Tasks that must be pre-installed:

```bash
# Verify required cluster tasks are present
oc get task -n tekton-tasks git-clone maven buildah trivy-scanner openshift-client send-to-webhook-slack
```

---

## 2. Deployment Artifact Inventory

### 2.1 OpenShift Manifests (`deploy/openshift/`)

| File | Kind | Description |
|------|------|-------------|
| `namespace.yaml` | Namespace | `coolstore-dev`, `-staging`, `-prod` |
| `configmap.yaml` | ConfigMap | Non-sensitive runtime config (Quarkus props, Flyway, messaging) |
| `secret.yaml` | Secret | Credential placeholders — **replace before applying** |
| `pvc.yaml` | PersistentVolumeClaim | 5 Gi `gp3-csi` audit-log PVC |
| `deployment.yaml` | Deployment | 2-replica app deployment; restricted-v2 SCC hardening |
| `service.yaml` | Service | ClusterIP on port 8080 |
| `route.yaml` | Route | Edge-TLS; sticky sessions (`balance: source`) |
| `hpa.yaml` | HorizontalPodAutoscaler | min 2 / max 10; CPU 70% / Memory 80% |
| `postgresql.yaml` | PostgresCluster | CrunchyData PGO CR — PostgreSQL 14, 10 Gi |
| `keycloak.yaml` | Keycloak + KeycloakRealmImport | Keycloak Operator CR + coolstore realm |
| `networkpolicy.yaml` | NetworkPolicy (×8) | Default-deny + explicit allow rules |
| `servicemonitor.yaml` | ServiceMonitor | Prometheus scrape config for `/q/metrics` |
| `argocd-application.yaml` | Application | ArgoCD CR — automated prune + selfHeal |
| `kustomization.yaml` | Kustomization | Kustomize manifest aggregator |

### 2.2 AMQ Manifests (`deploy/amq/`)

| File | Kind | Description |
|------|------|-------------|
| `activemqartemis-cr.yaml` | ActiveMQArtemis + ActiveMQArtemisAddress | Broker CR; `topic/orders` multicast + DLQ |

### 2.3 Tekton Pipeline (`deploy/pipelines/`)

| File | Kind | Description |
|------|------|-------------|
| `pipeline.yaml` | Pipeline | 5-stage: clone → build → image-build → scan → deploy |
| `pipeline-run.yaml` | PipelineRun | Sample run targeting `coolstore-dev` |
| `maven-cache-pvc.yaml` | PersistentVolumeClaim | 2 Gi Maven local-repo cache for Tekton |

### 2.4 Container Build (`coolstore-quarkus/`)

| File | Description |
|------|-------------|
| `Containerfile.jvm` | Multi-stage JVM build (UBI 9 / OpenJDK 17) |
| `Containerfile.native` | Multi-stage native binary build (UBI Micro) |
| `.dockerignore` | Excludes secrets, test sources, build output |

---

## 3. Deployment Procedures

### 3.1 Manual Deployment (step-by-step)

```bash
# 0. Verify cluster access
oc whoami
oc whoami --show-server

# 1. Create namespaces
oc apply -f deploy/openshift/namespace.yaml

# 2. ⚠️  REQUIRED: Replace secret placeholders before this step
#    See Section 4 (Secrets) for options.
oc apply -f deploy/openshift/secret.yaml -n coolstore-dev

# 3. ConfigMap + storage
oc apply -f deploy/openshift/configmap.yaml -n coolstore-dev
oc apply -f deploy/openshift/pvc.yaml       -n coolstore-dev

# 4. Provision network isolation
oc apply -f deploy/openshift/networkpolicy.yaml -n coolstore-dev

# 5. Deploy infrastructure via operator CRs
oc apply -f deploy/openshift/postgresql.yaml        -n coolstore-dev
oc apply -f deploy/amq/activemqartemis-cr.yaml      -n coolstore-dev
oc apply -f deploy/openshift/keycloak.yaml           -n coolstore-dev

# Wait for infrastructure to be ready (up to 5 minutes each)
oc wait --for=condition=Ready \
  postgrescluster/coolstore-db \
  -n coolstore-dev --timeout=300s

oc wait --for=jsonpath='{.status.deploymentPlanSize}'=1 \
  activemqartemis/coolstore-amq-broker \
  -n coolstore-dev --timeout=300s

# 6. Build and push the application image
cd coolstore-quarkus
podman build -f Containerfile.jvm \
  -t quay.io/coolstore/coolstore-quarkus:latest .
podman push quay.io/coolstore/coolstore-quarkus:latest
cd ..

# 7. Deploy the application
oc apply -f deploy/openshift/deployment.yaml -n coolstore-dev
oc apply -f deploy/openshift/service.yaml    -n coolstore-dev
oc apply -f deploy/openshift/route.yaml      -n coolstore-dev
oc apply -f deploy/openshift/hpa.yaml        -n coolstore-dev

# 8. Enable Prometheus scraping
oc apply -f deploy/openshift/servicemonitor.yaml -n coolstore-dev

# 9. Wait for rollout
oc rollout status deployment/coolstore-quarkus \
  -n coolstore-dev --timeout=300s

# 10. Verify
ROUTE=$(oc get route coolstore-quarkus -n coolstore-dev \
  -o jsonpath='{.spec.host}')
curl -sk "https://$ROUTE/q/health"
```

### 3.2 Kustomize (single command)

```bash
# Apply all resources in dependency order via Kustomize
oc apply -k deploy/openshift/ -n coolstore-dev
```

> **Note**: Kustomize apply does not wait for CRs to be ready.
> Run the `oc wait` commands from Step 5 above after applying.

### 3.3 GitOps via ArgoCD (recommended for production)

```bash
# Install the ArgoCD Application CR — ArgoCD syncs all manifests automatically
oc apply -f deploy/openshift/argocd-application.yaml

# Monitor sync status
oc get application coolstore-quarkus -n openshift-gitops -w

# Force immediate sync if needed
argocd app sync coolstore-quarkus
```

ArgoCD is configured with:
- **Automated sync** — every 3 minutes (default polling interval)
- **Prune** — removes resources deleted from Git
- **Self-heal** — reverts out-of-band cluster changes
- **Retry** — 5 attempts with exponential backoff (5 s → 3 m max)

### 3.4 Tekton CI/CD Pipeline

```bash
# 1. Create the Maven cache PVC (one-time setup)
oc apply -f deploy/pipelines/maven-cache-pvc.yaml -n coolstore-dev

# 2. Create the registry push secret (one-time setup)
oc create secret docker-registry quay-push-secret \
  --docker-server=quay.io \
  --docker-username=<robot-account> \
  --docker-password=<token> \
  -n coolstore-dev

# 3. Install the pipeline
oc apply -f deploy/pipelines/pipeline.yaml -n coolstore-dev

# 4. Trigger a run
oc apply -f deploy/pipelines/pipeline-run.yaml -n coolstore-dev

# 5. Watch progress
tkn pipelinerun logs --last -f -n coolstore-dev
```

---

## 4. Secrets Management

> **⚠️  Critical**: `deploy/openshift/secret.yaml` contains placeholder values
> (`REPLACE_WITH_ACTUAL_*`). **Never apply the file as-is to a production cluster.**

### Option A — `oc create secret` (dev/test)

```bash
oc create secret generic coolstore-quarkus-secret \
  --from-literal=DB_URL="jdbc:postgresql://coolstore-db-primary.coolstore-dev.svc:5432/coolstoredb" \
  --from-literal=DB_USERNAME="coolstore" \
  --from-literal=DB_PASSWORD="<actual-password>" \
  --from-literal=AMQP_HOST="coolstore-amq-broker-hdls-svc.coolstore-dev.svc" \
  --from-literal=AMQP_PORT="5672" \
  --from-literal=AMQP_USERNAME="admin" \
  --from-literal=AMQP_PASSWORD="<actual-password>" \
  --from-literal=OIDC_AUTH_SERVER_URL="https://keycloak.coolstore-dev.apps.<cluster>/realms/coolstore" \
  --from-literal=OIDC_CLIENT_ID="coolstore" \
  --from-literal=OIDC_CLIENT_SECRET="<actual-secret>" \
  --from-literal=SESSION_ENCRYPTION_KEY="<32-char-random-key>" \
  -n coolstore-dev
```

### Option B — External Secrets Operator + Vault (production)

See `docs/adr/ADR-004-secret-rotation-strategy.md` for the full layered secret
management design. Summary:

1. HashiCorp Vault stores all credentials (dynamic DB creds via PG secrets engine).
2. External Secrets Operator (ESO) syncs secrets into namespace-scoped OpenShift
   Secrets on a 1-hour TTL.
3. Quarkus pods consume secrets via `envFrom.secretRef` — no changes required
   to the deployment manifest.

---

## 5. Network Isolation (`deploy/openshift/networkpolicy.yaml`)

Eight NetworkPolicy resources are applied to `coolstore-dev`:

| Policy | Selector | Allows |
|--------|----------|--------|
| `default-deny-ingress` | All pods | Nothing — baseline deny |
| `allow-router-to-app` | `app: coolstore-quarkus` | Router → 8080/8443 |
| `allow-intra-namespace` | `app: coolstore-quarkus` | Same-namespace pods → 8080 |
| `allow-prometheus-scrape` | `app: coolstore-quarkus` | `openshift-user-workload-monitoring` → 8080 |
| `allow-tekton-to-app` | `app: coolstore-quarkus` | `openshift-pipelines` → 8080 |
| `allow-app-to-postgres` | CrunchyData DB pods | App → 5432 |
| `allow-app-to-amq` | Artemis broker pods | App → 5672, 8161 |
| `allow-router-to-keycloak` | Keycloak pods | Router → 8080/8443 |

---

## 6. Observability

### 6.1 Health Probes

| Probe | Endpoint | Initial Delay | Period | Fail Threshold |
|-------|----------|--------------|--------|----------------|
| Liveness | `/q/health/live` | 30 s | 10 s | 3 |
| Readiness | `/q/health/ready` | 15 s | 5 s | 3 |

### 6.2 Prometheus Metrics (`deploy/openshift/servicemonitor.yaml`)

- **Scrape endpoint**: `/q/metrics` (Micrometer + SmallRye Metrics)
- **Interval**: 30 s
- **Scrape timeout**: 10 s
- **Relabelling**: adds `application`, `namespace`, and `pod` labels to every series

Enable user-workload monitoring and label the namespace before applying the ServiceMonitor (see Section 1.2).

### 6.3 Useful Commands

```bash
# Live logs
oc logs -f deployment/coolstore-quarkus -n coolstore-dev

# Health check
oc exec deploy/coolstore-quarkus -n coolstore-dev \
  -- curl -s http://localhost:8080/q/health | jq .

# Metrics snapshot
oc exec deploy/coolstore-quarkus -n coolstore-dev \
  -- curl -s http://localhost:8080/q/metrics | head -40

# Pod resource usage
oc adm top pods -n coolstore-dev

# HPA status
oc get hpa coolstore-quarkus-hpa -n coolstore-dev -w

# AMQ dead-letter queue
oc port-forward svc/coolstore-amq-broker-console-jolokia-0-svc \
  8161:8161 -n coolstore-dev
# Browser: http://localhost:8161/console
```

---

## 7. Rollback Procedure

### Application Rollback

```bash
# View rollout history
oc rollout history deployment/coolstore-quarkus -n coolstore-dev

# Rollback to previous revision
oc rollout undo deployment/coolstore-quarkus -n coolstore-dev

# Rollback to specific revision
oc rollout undo deployment/coolstore-quarkus \
  -n coolstore-dev --to-revision=<N>

# Verify pods
oc get pods -n coolstore-dev -l app=coolstore-quarkus
```

### GitOps Rollback (recommended)

```bash
# Revert the offending commit in Git, then push — ArgoCD syncs automatically.
git revert HEAD
git push origin konveyor/migration-1794838418

# Or force ArgoCD to sync to a specific Git SHA
argocd app set coolstore-quarkus --revision <good-sha>
argocd app sync coolstore-quarkus
```

---

## 8. Dependency Health Checks

```bash
# PostgreSQL (CrunchyData PGO)
oc get postgrescluster coolstore-db -n coolstore-dev
oc get pods -n coolstore-dev -l \
  postgres-operator.crunchydata.com/cluster=coolstore-db

# AMQ Artemis
oc get activemqartemis coolstore-amq-broker -n coolstore-dev
oc get pods -n coolstore-dev | grep amq

# Keycloak
oc get keycloak coolstore-keycloak -n coolstore-dev
oc get pods -n coolstore-dev | grep keycloak

# Connectivity test from app pod
APP_POD=$(oc get pods -n coolstore-dev \
  -l app=coolstore-quarkus \
  -o jsonpath='{.items[0].metadata.name}')

# DB connectivity
oc exec "$APP_POD" -n coolstore-dev -- \
  bash -c 'timeout 3 bash -c \
    "</dev/tcp/coolstore-db-primary.coolstore-dev.svc/5432" \
    && echo "DB OK" || echo "DB FAIL"'

# AMQP connectivity
oc exec "$APP_POD" -n coolstore-dev -- \
  bash -c 'timeout 3 bash -c \
    "</dev/tcp/coolstore-amq-broker-hdls-svc.coolstore-dev.svc/5672" \
    && echo "AMQP OK" || echo "AMQP FAIL"'
```

---

## 9. Changes Made by This Stage

The following changes were made to the repository on branch
`konveyor/migration-1794838418` by the MigIQ Deployment Agent:

### New Files

| File | Description |
|------|-------------|
| `deploy/openshift/networkpolicy.yaml` | 8 NetworkPolicy resources — default-deny + explicit allow rules |
| `deploy/openshift/servicemonitor.yaml` | Prometheus ServiceMonitor for `/q/metrics` scraping |
| `deploy/pipelines/maven-cache-pvc.yaml` | 2 Gi PVC for Tekton Maven repository cache |

### Modified Files

| File | Change |
|------|--------|
| `deploy/openshift/argocd-application.yaml` | Corrected `repoURL` and `targetRevision` from placeholder values |
| `deploy/openshift/kustomization.yaml` | Added `networkpolicy.yaml` and `servicemonitor.yaml` to resource list |
| `deploy/pipelines/pipeline.yaml` | Fixed `DOCKERFILE` path (`Containerfile.jvm`); fixed deployment name (`coolstore-quarkus`); corrected default `git-url` and `git-revision` |
| `deploy/pipelines/pipeline-run.yaml` | Corrected `git-url` and `git-revision` to match actual repository and branch |

---

## 10. Known Limitations and Next Steps

| Item | Priority | Notes |
|------|----------|-------|
| Replace secret placeholders | 🔴 High | Required before first deploy; use Vault/ESO for production |
| Build and push image to Quay.io | 🔴 High | `podman build/push` or Tekton pipeline |
| Tekton cluster tasks availability | 🔴 High | Verify `git-clone`, `maven`, `buildah`, `trivy-scanner`, `openshift-client` are installed |
| Keycloak realm import — first-run only | 🟡 Medium | Subsequent realm changes via admin console; see `docs/operator-runbook.md` |
| `ReadWriteOnce` audit-log PVC | 🟡 Medium | Rolling updates work (maxUnavailable: 0); switch to `ReadWriteMany` for multi-node parallelism |
| Session stickiness | 🟡 Medium | `@SessionScoped` cart is in-memory; Route sticky session configured; consider Redis for HA |
| Prometheus ServiceMonitor | 🟡 Medium | Enable user-workload monitoring first (Section 1.2) |
| NetworkPolicy egress rules | 🟡 Medium | Only ingress is restricted; add egress rules for stricter isolation |
| Native build pipeline stage | 🟢 Low | `Containerfile.native` available; add a parallel Tekton task if native image is desired |
| 48-hour parallel run in `coolstore-staging` | 🟢 Story 8 | Canary traffic splitting after dev validation |
| OWASP ZAP security scan | 🟢 Story 8 | Add as a Tekton task after deploy-dev |
| Monolith decommission | 🟢 Post-Story 8 | 30-day stability window; see `mig-plan-workspace/UserStory.md` |

---

## References

- `docs/containerization-report.md` — Base image selection, security hardening, build strategy
- `docs/operator-runbook.md` — Day-2 operations (scale, restart, rollback, audit logs)
- `docs/adr/ADR-001-quarkus-over-spring-boot.md` — Framework decision
- `docs/adr/ADR-004-secret-rotation-strategy.md` — Secret lifecycle management
- `deploy/amq/broker-config.md` — AMQ Broker topology and dead-letter queue runbook
- [OpenShift NetworkPolicy documentation](https://docs.openshift.com/container-platform/latest/networking/network_policy/about-network-policy.html)
- [Prometheus Operator ServiceMonitor](https://prometheus-operator.dev/docs/operator/api/#monitoring.coreos.com/v1.ServiceMonitor)
- [CrunchyData PGO](https://access.crunchydata.com/documentation/postgres-operator/)
- [AMQ Broker Operator](https://access.redhat.com/documentation/en-us/red_hat_amq_broker)
- [Keycloak Operator](https://www.keycloak.org/operator/installation)
