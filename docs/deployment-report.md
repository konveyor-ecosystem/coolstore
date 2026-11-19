# Deployment Report: CoolStore Monolith

Deployment artifacts produced by the **mig-deploy** skill (OpenShift deployment with
dependency management), building on the mig-containerize output (`Containerfile`,
`openshift/*.yaml`, `docs/containerization-report.md`).

## Executive Summary

- **Application**: CoolStore monolith (Quarkus 3.15.1 / Java 21, fast-jar, UBI9 images)
- **Target platform**: OpenShift (namespace `coolstore` by default)
- **Primary artifact**: `openshift/deploy.sh` — end-to-end deployment automation implementing the full mig-deploy workflow (prerequisites → namespace → dependencies → image build/push → ordered manifest deploy → rollout → health verification → report → rollback)
- **Cluster execution**: **not performed in this environment** — no `oc`/`podman`/`docker`/`helm` CLIs are installed here. The deployment was fully specified and validated offline via `--dry-run` (see *Validation* below). The Tekton CI pipeline (`.tekton/pipeline.yaml`) builds and scans the image in-cluster; `deploy.sh` drives the runtime deployment.
- **rgctl index**: `.rgctl/` holds `migration_plan.json` only (graph artifacts are ephemeral/gitignored), so endpoint testing uses the **source-derived endpoint list** (documented below). If a live index exists, `deploy.sh` switches to graph-driven endpoint discovery automatically.

## Deployment Artifacts

| File | Purpose |
|------|---------|
| `openshift/deploy.sh` | Deployment automation: prerequisites check, namespace handling, dependency strategy, image build/push, safe-order manifest apply, rollout wait, health verification, deployment report, rollback |
| `openshift/deployment.yaml` … `smoke-test.yaml` | Workload/infra manifests from mig-containerize (consumed by `deploy.sh`) |
| `Containerfile` | UBI9 multi-stage image build (consumed by `deploy.sh` Step 6) |
| `docs/deployment-report.md` | This report |

## Deployment Workflow (implemented by `openshift/deploy.sh`)

1. **Prerequisites** — verifies mig-containerize artifacts (hard fail if missing), `oc` login, and optionally the rgctl index for graph-driven endpoint testing.
2. **Namespace** — target `coolstore` (override with `-n`/`NAMESPACE`); creates it on request (`--create-ns` or interactive prompt).
3. **Dependency detection** — from the containerization output: PostgreSQL (StatefulSet), Keycloak (Deployment), Artemis broker (Deployment).
4. **Existing-dependency check** — reuses workloads already present in the namespace; only missing ones are deployed.
5. **Dependency handling** — strategies: `yaml` (default, applies the generated manifests), `helm` (`--helm-charts dep=chart`), `operator` (Subscription guidance), or `skip` (external/managed services). Database schema is handled in-app by Flyway at startup (baseline-on-migrate, baseline 1.2) — no separate migration Job. The `orders` AMQP address is created by the app on connect.
6. **Image build & push** — detects the OpenShift internal registry (`default-route` or `image-registry.openshift-image-registry.svc:5000`), builds from `Containerfile` with podman/docker, logs in with the `oc` token, pushes `$REGISTRY/$NAMESPACE/coolstore-monolith:$TAG`. `--skip-build` reuses an existing image.
7. **Ordered manifest apply** — Secrets (guarded: refuses `CHANGE_ME` placeholders unless `--allow-example-secrets`) → keycloak-realm ConfigMap (`realm-export.json`) → `coolstore-config` ConfigMap → dependencies → Deployment (image rewritten to the pushed tag) → Service → Route → HPA. PVCs are embedded in the PostgreSQL StatefulSet; no ServiceAccount/RBAC is required.
8. **Rollout wait** — `oc rollout status` (300s timeout); on failure prints pod status + logs and the rollback command.
9. **Health verification** — pods (phase + Ready), in-cluster Service probe, Route probe, endpoint tests, dependency connectivity (`/dev/tcp` from an app pod), and the `coolstore-smoke-test` Job (health + products endpoint).
10. **Deployment report** — cluster/namespace/user/timestamp, replicas, image, route URL, dependency status, pods, recent events; optionally saved with `--report-file`.

## Health Verification Endpoints (source-derived)

`quarkus.rest.path=/services`; OIDC realm `eap` secures checkout/orders (public paths: `/`, `/q/*`, `/services/products/*`, `/services/cart/*`).

| Method | Path | Expected | Rationale |
|--------|------|----------|-----------|
| GET | `/q/health/live` | 200 | SmallRye Health liveness |
| GET | `/q/health/ready` | 200 | Readiness (DB + AMQP checks) |
| GET | `/services/products` | 200 | Public product catalog |
| GET | `/services/cart/deploy-probe` | 200 | Public cart read (creates empty cart) |
| GET | `/services/orders` | 401/403 | OIDC-secured — reachable-with-auth is a pass signal |
| POST | `/services/cart/checkout/deploy-probe` | 401/403 | OIDC-secured checkout |

Dependency connectivity targets: `postgresql:5432`, `keycloak:80`, `broker:5672`.

## Usage

```bash
oc login <cluster-url>                       # prerequisite
./openshift/deploy.sh                        # full deploy to namespace "coolstore"
./openshift/deploy.sh -n coolstore-dev --create-ns --image-tag v1.0.0
./openshift/deploy.sh --verify-only          # re-run health checks only
./openshift/deploy.sh --dry-run              # print the plan, touch nothing
./openshift/deploy.sh --report-file deploy-report.txt
./openshift/deploy.sh --rollback             # oc rollout undo
```

Secrets: create real credentials first (never commit them):
`oc create secret generic coolstore-secrets --from-literal=QUARKUS_DATASOURCE_PASSWORD=… \
--from-literal=AMQP_PASSWORD=… --from-literal=KEYCLOAK_ADMIN_PASSWORD=…` (all keys in
`openshift/secret.example.yaml`), or pass `--secret-file`.

## Validation Performed (this environment)

- `bash -n openshift/deploy.sh` — syntax clean
- `./openshift/deploy.sh --help` — usage renders correctly
- `./openshift/deploy.sh --dry-run` — full 10-step plan executes end-to-end (exit 0): dependency
  detection (3), safe apply order, image path, endpoint list, connectivity probes, smoke job, report
- Mode/flag matrix: `--verify-only`, `--rollback`, `--dep-strategy skip|helm` (with and without
  `--helm-charts`), `-n/--create-ns/--image-tag/--skip-build/--report-file`, unknown-flag error
- YAML manifests were syntax-validated during mig-containerize (report there); dry-run re-checks
  them when PyYAML is available (not installed here — degrades with a warning)
- Bugs found & fixed during validation: dry-run no longer requires the `helm` binary;
  report mode labels no longer mislabel runs as `verify-only`

## Rollback Procedure

```bash
./openshift/deploy.sh --rollback             # or: oc rollout undo deployment/coolstore-monolith -n coolstore
oc rollout history deployment/coolstore-monolith -n coolstore
oc get pods -n coolstore && oc logs deployment/coolstore-monolith -n coolstore
```

Schema is unchanged from the EAP era (Flyway `baseline-on-migrate`), so the legacy EAP WAR can be
redeployed against the same PostgreSQL during the rollback window (see `docs/migration-runbook.md`).

## Known Issues and Limitations

1. **No live cluster in this environment** — `deploy.sh` is validated by dry-run only; the first live run should be against a dev namespace, with the smoke-test Job green before promotion.
2. **rgctl graph endpoints are heuristic** — when an index is present, graph-derived endpoint names are reported but assertions use the curated list above.
3. **Operator strategy is guidance-only** — `--dep-strategy operator` prints Subscription instructions but does not author operator-specific CustomResources.
4. **External audit collector** — `AUDIT_STREAM_HOST` points at an `audit-collector` service that is not part of these manifests (async, failures tolerated by the app).

## Next Steps

1. ⏳ Live deploy: `oc login` → `./openshift/deploy.sh -n coolstore` → review `--report-file` output
2. ⏳ Review the Tekton/Trivy image scan before promotion (see `docs/containerization-report.md`)
3. ⏳ Point `AUDIT_STREAM_HOST` at a real audit collector service
4. ⏳ Load testing; tune HPA thresholds/resources
5. ⏳ Production cutover per `docs/migration-runbook.md`
