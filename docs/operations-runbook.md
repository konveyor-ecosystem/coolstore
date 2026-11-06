# Operations Runbook — CoolStore on OpenShift

Day-2 procedures for the CoolStore Quarkus deployment (namespace `coolstore`).

## Deployment order

The application depends on its backing services; apply in this order:

```bash
oc apply -f openshift/postgresql.yaml          # 1. database
oc apply -f openshift/amq-broker.yaml          # 2. JMS broker (topic/orders)
oc create configmap keycloak-realm \           # 3a. realm prerequisite
  --from-file=realm-export.json=realm-export.json
oc apply -f openshift/keycloak.yaml            # 3b. identity provider
oc apply -f openshift/coolstore-quarkus.yaml   # 4. the application
```

`oc apply -f openshift/` also works — files are applied alphabetically, which
matches the required order — but the `keycloak-realm` ConfigMap must still be
created manually first (see the header of `openshift/keycloak.yaml`); Keycloak
pods wait for it, and the application waits for Keycloak via its readiness
probe. Replace every `CHANGE_ME` in the Secrets before deploying to a shared
cluster. CI (`build-openshift.yml`) runs `oc apply -f openshift/` on pushes to
`main`, then pins the deployment to `quay.io/coolstore/coolstore-quarkus:<sha>`.

## Scaling

- The `HorizontalPodAutoscaler` keeps **2–5 replicas at 80% average CPU**.
- Check: `oc get hpa coolstore-quarkus -w`.
- Temporary manual scale (the HPA will take over again):
  `oc scale deployment/coolstore-quarkus --replicas=4`.
- To pin capacity, change `minReplicas`/`maxReplicas` in
  `openshift/coolstore-quarkus.yaml` rather than scaling manually.
- Replicas ≥ 2 plus the rolling-update strategy replace the legacy EAP
  full-HA cluster: carts persist in PostgreSQL and orders flow through the
  Artemis topic, so any replica can serve any request.

## Rolling updates (zero downtime)

`maxUnavailable: 0` + `maxSurge: 1`: a new pod must pass its readiness probe
before any old pod is terminated — the Route never sends traffic to a cold
pod.

```bash
oc rollout status deployment/coolstore-quarkus    # watch a rollout
oc rollout history deployment/coolstore-quarkus
```

## Health probes

| Probe | Path | Meaning | On failure |
|---|---|---|---|
| startup | `/q/health/started` (30 × 5 s) | app booted, incl. Flyway migration | tolerates up to 150 s slow start |
| readiness | `/q/health/ready` (5 s delay, 5 s period) | can serve traffic (DB/broker checks) | pod removed from Service endpoints |
| liveness | `/q/health/live` (10 s delay, 10 s period) | process is not wedged | pod restarted |

## Monitoring

- **Metrics**: `quarkus-micrometer` exposes Prometheus metrics at `/q/metrics`.
  With user-workload monitoring enabled, scrape the pods:
  ```yaml
  apiVersion: monitoring.coreos.com/v1
  kind: ServiceMonitor
  metadata:
    name: coolstore-quarkus
    namespace: coolstore
  spec:
    selector:
      matchLabels:
        app: coolstore-quarkus
    endpoints:
      - port: http
        path: /q/metrics
  ```
- **Logs**: JSON structured logs via `quarkus-logging-json`:
  `oc logs -f deployment/coolstore-quarkus` (one JSON object per line).
- **Events**: `oc get events -n coolstore --sort-by=.lastTimestamp`.

## Rollback procedure

1. **Image rollback** (bad release, application level):
   ```bash
   oc rollout undo deployment/coolstore-quarkus
   # or pin a known-good tag:
   oc set image deployment/coolstore-quarkus \
     coolstore-quarkus=quay.io/coolstore/coolstore-quarkus:<known-good-sha>
   ```
2. **Schema rollback** (bad migration): restore the latest `pg_dump` backup —
   see [persistence-runbook.md](persistence-runbook.md).
3. **Full rollback to the legacy stack**: the legacy EAP 7.4 WAR remains
   deployable against the same database (zero data migration). Redeploy
   `ROOT.war` on the EAP cluster (steps in the README "Legacy deployment"
   section), then switch the OpenShift `coolstore` Route to the legacy
   Service:
   ```bash
   oc patch route coolstore --type=merge \
     -p '{"spec":{"to":{"kind":"Service","name":"<legacy-service>"}}}'
   ```
   Switch the Route back the same way once the Quarkus deployment is healthy.

## Troubleshooting

| Symptom | Likely cause | Fix |
|---|---|---|
| `CrashLoopBackOff` on coolstore-quarkus | wrong/missing DB or broker credentials (`CHANGE_ME` left in Secrets) | `oc logs`, fix Secret values, `oc rollout restart` |
| Readiness never turns green | DB unreachable, Flyway failed, or Artemis down | check `postgresql`/`amq-broker` pods and app logs |
| `ImagePullBackOff` | tag not pushed / Quay permissions | confirm the image exists in Quay; check pull secret |
| Keycloak `CreateContainerConfigError` | `keycloak-realm` ConfigMap missing | run the `oc create configmap` prerequisite from `openshift/keycloak.yaml` |
| 401 on `/services/cart` or `/services/orders` | OIDC misconfiguration | see [security-oidc.md](security-oidc.md) |
| HPA stuck at `minReplicas` | metrics-server not scraping / no CPU requests | verify resource `requests` (set in the Deployment) and metrics-server |
| PVC `Pending` | no default StorageClass or quota exceeded | ask the cluster admin, or adjust the PVCs in `openshift/*.yaml` |
