# Deployment Report: CoolStore (Quarkus 3 monolith) — mig-deploy stage

## Executive Summary

- **Outcome**: deployment is fully prepared and automated via **`deploy.sh`**
  (one command, follows the mig-deploy skill workflow end-to-end), but the
  **live cluster rollout could not be executed from this stage environment** —
  the stage agent's service account has **no resource-creation rights** on the
  target cluster (evidence below). All manifests were re-verified, the rgctl
  index was rebuilt, smoke-test endpoints were derived from the code graph,
  and the frontend OIDC blocker (plan task 7.2) was fixed with a deploy-time
  ConfigMap overlay.
- **Application**: `coolstore-quarkus` (Quarkus 3.15 / Java 21, UBI 9 image),
  namespace `coolstore`, 2 replicas (HPA 2–5), image `quay.io/coolstore/coolstore-quarkus:latest`
- **Dependencies**: PostgreSQL 16 (`database-statefulset.yaml`), ActiveMQ
  Artemis (`amq-broker.yaml`), Keycloak 26 (`keycloak.yaml`, realm `eap`);
  audit collector intentionally not deployed (see CONTAINERIZATION_REPORT.md)

## Why the rollout did not run here (blocker + evidence)

The mig-deploy skill requires `oc login`; this stage runs as a pod
(`ui-bnt9q-deploy`) in namespace `openshift-mta` on the target cluster, with
only its default service account. Findings (all verified via the Kubernetes
API at `https://172.30.0.1:443`):

| Check | Result |
|---|---|
| Identity (`SelfSubjectReview`) | `system:serviceaccount:openshift-mta:default` |
| `SelfSubjectRulesReview` (coolstore / openshift-mta) | 23–24 rules — basic-user + build-strategy only; **no `create` on any workload resource**; `projectrequests` create denied; visible projects: **none** |
| SSAR matrix (create secrets/configmaps/services/pods/PVCs/deployments/statefulsets/builds/buildconfigs/imagestreams/routes/HPAs/SAs — in `coolstore`, `openshift-mta`, cluster-wide) | **all denied** |
| `oc` / `kubectl` / `podman` / `docker` binaries in stage environment | **not installed** (curl/python3/git/rgctl only) |
| Alternate kubeconfig / tokens | none (checked `~`, `/workspace`, env, `/proc/mounts`) |
| CI path (`.github/workflows/build-openshift.yml`) | deploy job requires `OPENSHIFT_SERVER` / `OPENSHIFT_TOKEN` repo secrets — not present here |

Consequence: applying manifests, building via OpenShift Builds, or pushing to
the internal registry are all RBAC-blocked for this identity. The deployment
therefore targets the next runner with credentials — a developer with `oc
login`, or CI once the secrets above are configured.

## rgctl Analysis (deploy phase)

Index was missing (`GRAPH_NOT_FOUND`) → re-discovered per mig-rgctl workflow:

```
rgctl discover . --with-cfg --with-security --with-taint --with-harmonic   # finished in 17.1s
rgctl -f json metrics --pagerank                                           # ✓ index ready
```

Graph-derived smoke-test endpoints (corroborated against `@Path` annotations;
REST root is `quarkus.rest.path=/services` in `application.properties`):

| Endpoint class (graph) | Route | Auth policy | Expected |
|---|---|---|---|
| `ProductEndpoint` `@Path(/products)` | `GET /services/products` | `public.paths=/services/products*` → permit | **200** |
| `OrderEndpoint` `@Path(/orders)` | `GET /services/orders` | `authenticated` (OIDC) | **401** without token |
| `CartEndpoint` `@Path(/cart)` | `GET /services/cart/{cartId}` | `authenticated` (OIDC) | **401** without token |
| smallrye-health | `GET /q/health/ready` | not under `/services/*` | **200** |

The 401 expectations double as a positive test that OIDC enforcement is wired
(Keycloak realm `eap`, client `eap-app`). A full login/logout E2E (plan task
7.3) needs a browser or token flow — see Next Steps.

## Deliverables (this stage)

| Change | Purpose |
|---|---|
| **`deploy.sh`** (new, executable) | One-command deployment: prerequisites → namespace → runtime-generated secrets → Keycloak realm ConfigMap → dependencies (with readiness waits) → build/push image (internal registry default, Quay optional) → frontend OIDC ConfigMap → app manifests → rollout wait → health + graph-derived smoke tests. Idempotent; `SKIP_BUILD=1`, `NAMESPACE=…`, `QUAY_*` env overrides. |
| **`deployment.yaml`** (edited) | Optional `frontend-oidc` ConfigMap volume mounted over the image's baked-in `keycloak.json` (plan task 7.2 — externalize `auth-server-url`). `optional: true` keeps the pod schedulable without it. |
| **`DEPLOYMENT_REPORT.md`** (this file) | Stage findings, blocker evidence, run/rollback instructions. |

Secrets policy: `deploy.sh` generates all credentials at runtime
(`openssl rand`) and creates them only if absent — the committed `CHANGE_ME`
placeholders in `secret.yaml` are never applied, and no real credentials enter
git. App ↔ service credential pairs (`coolstore-db`↔`postgresql-creds`,
`coolstore-broker`↔`amq-creds`) are generated with matching values.

Frontend OIDC (task 7.2): `deploy.sh` fetches the Keycloak route host and
overlays `auth-server-url` into ConfigMap `frontend-oidc`; the realm client
`eap-app` already uses wildcard `redirectUris`/`webOrigins` (verified in
`realm-export.json`), so no realm patching is needed.

## How to deploy (next runner with credentials)

```bash
oc login <cluster-url>          # any identity with project-create + build rights
cd <repo root>
./deploy.sh                     # or: NAMESPACE=coolstore QUAY_NAMESPACE=... ./deploy.sh
```

Manual equivalent (skill apply order — secrets → configmaps → PVCs → deps →
app → routes) is documented in CONTAINERIZATION_REPORT.md; `deploy.sh`
encodes the same order including dependency readiness waits.

Rollback:

```bash
oc rollout undo deployment/coolstore-quarkus -n coolstore
oc delete namespace coolstore   # full teardown (removes PVCs with it)
```

## Verification performed in this stage

- All eight manifests re-read and cross-checked (env wiring vs
  `application.properties`, probe paths, ports, labels/selectors, RBAC-free
  `restricted-v2`-compatible pod specs)
- `deploy.sh` passes `bash -n`; `deployment.yaml` edit structurally verified
- rgctl index rebuilt and queried (endpoints above)
- Cluster RBAC posture mapped exhaustively (SSRR + SSAR matrix, evidence above)
- Realm `eap` client config verified for on-cluster SSO compatibility

## Known issues / next steps

1. **Execute `deploy.sh`** with a privileged identity (developer or CI with
   `OPENSHIFT_SERVER`/`OPENSHIFT_TOKEN` secrets) and record its smoke-test
   output here.
2. Plan task 7.3 (browser login/logout E2E against Keycloak) — after deploy,
   `https://<keycloak-route>/realms/eap/.well-known/openid-configuration`
   should 200; then exercise the SPA flow.
3. Audit collector (`audit-collector:1514`) still not provisioned — audit
   events silently dropped by design.
4. Base images tagged `:latest` — pin digests for production promotion.
5. In-memory carts remain per-pod (documented in CONTAINERIZATION_REPORT.md).
