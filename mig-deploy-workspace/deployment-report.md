# Deployment Report: Coolstore Monolith → OpenShift

**Branch:** `konveyor/migration-1795103297` · **Date:** 2026-11-19 · **Phase:** mig-deploy (migration plan task 6.8)

## Executive Summary

- **Application:** coolstore — Java EE 7 e-commerce monolith (WildFly 26.1.3 on UBI8/OpenJDK 11, from mig-containerize)
- **Deliverable:** `scripts/deploy-openshift.sh` — an idempotent, fail-fast automation of the full mig-deploy workflow (prerequisites → namespace → dependency management → image build/push → ordered manifest apply → rollout → health verification → report → rollback), plus one new manifest and three deployment-correctness fixes to the containerization output
- **Cluster state:** ⚠ **Not deployed to a live cluster** — this MigIQ environment has no `oc` CLI and no container runtime. Per the skill's prerequisite check, run `oc login <cluster-url>` on a machine with `oc` + `podman`/`docker` and execute the runbook below; everything else is prepared and verified
- **Verification:** 18/18 manifest consistency checks, script syntax + full workflow smoke test against a stub cluster (exit 0, correct ordering), `--dry-run` and `rollback` modes tested, Maven `package` reproduces `target/ROOT.war` (16 MB)

## Prerequisite Check Results (skill Step 1)

| Prerequisite | Status | Notes |
|---|---|---|
| Containerization artifacts | ✅ | `Dockerfile`, `openshift/01–08`, `scripts/entrypoint.sh`, `scripts/wildfly-config.cli`, `realm-export.json` |
| OpenShift cluster access | ❌ | No `oc` binary in this environment — **user must run `oc login <cluster-url>`** before the runbook below |
| rgctl index | ⚠️ | Not present; endpoint testing falls back to code-derived paths (`/health.jsp`, `/`, `/services/products`, `/services/orders`) |

## Deployment-Correctness Fixes to the Containerization Output

Found during dependency analysis (skill Steps 3–5), all fixed in this phase:

1. **Keycloak unreachable from browsers** — `KEYCLOAK_AUTH_SERVER_URL` pointed at in-cluster service DNS (`http://keycloak:8080/`), but the SPA's OIDC adapter (`keycloak.json`) runs **in the browser**, which cannot resolve cluster DNS.
   - Added `openshift/08-keycloak-route.yaml` (edge-TLS Route for the keycloak Service)
   - `deploy-openshift.sh` creates the Route, then patches `KEYCLOAK_AUTH_SERVER_URL` in `coolstore-config` to `https://<keycloak-route-host>/` **before** deploying the app (so the first boot gets the right value; re-deploys trigger `oc rollout restart` to pick up ConfigMap changes)
   - Added `KC_PROXY: edge` to `openshift/04-keycloak.yaml` so Keycloak 20 honours `X-Forwarded-*` from the edge-TLS route
   - Verified `realm-export.json`: client `eap-app` has `redirectUris: ["*"]`, `webOrigins: ["*"]` — any Route origin works
2. **podman-compose stack fix** — same browser-reachability bug locally: `KEYCLOAK_AUTH_SERVER_URL` now `http://localhost:8081/` (matches the compose port map and the original hardcoded value)
3. **Placeholder secrets** — the script generates the DB Secret from `DB_USER`/`DB_PASSWORD` env vars and patches the Keycloak admin password from `KEYCLOAK_ADMIN_PASSWORD`, falling back to the manifest placeholders with loud warnings (dev only)

## Dependency Management (skill Steps 3–5)

| Dependency | Manifest | Handling |
|---|---|---|
| PostgreSQL 15 | `openshift/03-postgres.yaml` (StatefulSet + headless SVC + 10Gi PVC) | Deployed if missing, `oc wait` for Ready; schema migrated **in-process** by Flyway (`DataBaseMigrationStartup`, V1_1/V1_2) — no migration Job needed |
| Keycloak 20.0.5 | `openshift/04-keycloak.yaml` + `08-keycloak-route.yaml` | Deployed if missing; `eap` realm imported from `realm-export.json` via the `keycloak-realm` ConfigMap (created idempotently by the script); `oc rollout status` wait |
| External services | — | `--skip-deps` assumes managed/external PostgreSQL+Keycloak and only patches config if a keycloak Route exists |

Additional manifests the skill knows how to detect (`*mysql*`, `*kafka*`, `*redis*`, … in `openshift/`) are auto-applied if ever added.

## Deployment Order (skill Step 7 — verified via stub-cluster call log)

```
oc new-project coolstore
podman build -t <registry>/coolstore/coolstore-monolith:latest -f Dockerfile .
oc registry login  (fallback: podman login -u $(oc whoami) -p $(oc whoami -t))
podman push <image>
oc apply 02-secret → 01-configmap → keycloak-realm CM → 03-postgres → oc wait
oc apply 04-keycloak → rollout status → 08-keycloak-route → oc patch configmap
oc apply 05-deployment (image rewritten in a staged copy) → 06-service → 07-route
oc rollout status deployment/coolstore
```

The repo working tree is never modified — manifests are staged into a temp dir where namespace (`--namespace`) and image reference are rewritten.

## Health Verification Performed by the Script (skill Step 9)

1. Pod phase/ready checks (fatal on non-Running)
2. Service ClusterIP/port lookup
3. Route accessibility — `/health.jsp` via `curl -k` (200 expected; 000 warns and suggests in-cluster verification)
4. Endpoint smoke tests: `/health.jsp`, `/`, `/services/products`, `/services/orders` (200/301/302 pass; 401/403 = protected-OK). With an rgctl index present, endpoints are additionally derived from `*Endpoint` graph nodes
5. Dependency connectivity from the app pod (`/dev/tcp` to `postgres:5432`, `keycloak:8080`)
6. Flyway migration evidence from pod logs
7. Full deployment report (Step 10) to stdout and `--report-file`

## Verification Evidence (this environment)

| Check | Result |
|---|---|
| `bash -n scripts/deploy-openshift.sh` | ✅ syntax OK |
| YAML parse + 18 cross-manifest consistency checks (selectors, routes, secret/CM refs, probe paths/ports, KC_PROXY) | ✅ 18/18 |
| Stub-cluster smoke test (fresh-cluster path: namespace + deps missing, token-login fallback, report file) | ✅ exit 0, ordering correct |
| `rollback` subcommand | ✅ |
| `--dry-run` (no `oc`, no podman on PATH) | ✅ exit 0, 22 planned actions |
| `mvn -B clean package -DskipTests` | ✅ `target/ROOT.war` (16 MB) |
| `realm-export.json` client config | ✅ valid JSON, permissive origins |

## Runbook (where `oc` + `podman` are available)

```bash
oc login <cluster-url>
cd <repo>
DB_PASSWORD=<db-password> KEYCLOAK_ADMIN_PASSWORD=<kc-admin-password> \
  ./scripts/deploy-openshift.sh --report-file deployment-report.txt
# app:     https://$(oc get route coolstore  -n coolstore -o jsonpath='{.spec.host}')
# keycloak: https://$(oc get route keycloak -n coolstore -o jsonpath='{.spec.host}')
```

Options: `--namespace`, `--image-tag`, `--image`/`--skip-build`, `--skip-deps`, `--dry-run`, `rollback` (see `--help`).

## Rollback

```bash
./scripts/deploy-openshift.sh rollback          # oc rollout undo + status
oc logs -f deployment/coolstore -n coolstore    # diagnostics
```

## Known Issues and Limitations

- **No live deployment executed** — no `oc`/`podman` in the MigIQ environment (documented above); the smoke test used a stub cluster
- **Image build/scan deferred** — as in the containerization phase; run `grype $IMAGE` after the first real build
- **Keycloak admin password** — patched via env var by the script, but not yet a Secret (production: externalize)
- **No HPA** — 2 fixed replicas (parity with the EAP HA pair); revisit with the Quarkus re-base (plan TG4/TG7)
- **No JGroups HA clustering** — topic semantics give both replicas all `topic/orders` messages; session replication is a documented follow-up (containerization report)

## Next Steps

1. ⏳ Execute the runbook on a real cluster; attach `deployment-report.txt` to the migration record
2. ⏳ Task 6.9 — mig-test-gen smoke tests against `/health.jsp` + checkout flow on-cluster
3. ⏳ Task 6.10 — fold this runbook into the ops runbook; then TG7 cutover
