# CoolStore Monolith (Quarkus 3.15 / Java 21)

The coolstore monolith was migrated from Java EE 7 / JBoss EAP to **Quarkus 3.15 LTS on Java 21**. It runs on Red Hat OpenShift using UBI9 OpenJDK 21 images.
The REST API (`/services/products`, `/services/orders`, `/services/cart/...`) and its JSON are unchanged. The AngularJS UI is served from `src/main/resources/META-INF/resources`.

## Build
Requires JDK 21 and Maven 3.9+. `lib/audit-logging-library-2.0.0.jar` is installed into your local repo during the `validate` phase, so on a clean repo run `validate` first:
```bash
mvn -B validate && mvn -B package          # runs unit + @QuarkusTest tests (H2, no Docker needed)
java -jar target/quarkus-app/quarkus-run.jar
mvn quarkus:dev                            # dev mode (needs a PostgreSQL, see env vars)
```

## Configuration (env vars)
| Var | Default | Purpose |
|---|---|---|
| DB_HOST / DB_PORT / DB_NAME | localhost / 5432 / coolstore | PostgreSQL (Flyway migrates at start) |
| DB_USERNAME / DB_PASSWORD | coolstore / coolstore | DB credentials (from a Secret on OpenShift) |
| OIDC_ENABLED / OIDC_AUTH_SERVER_URL | false / http://localhost:8081 | Optional Keycloak realm `eap` bearer-token validation (the API stays anonymous) |
| AUDIT_ENABLED / AUDIT_STREAM_HOST / AUDIT_STREAM_PORT | true / localhost / 5170 | audit-logging-library 2.0.0 TCP collector. If no collector is reachable, events are skipped |

Health: `/q/health/live`, `/q/health/ready`.

## Architecture notes
- Checkout publishes to the in-memory SmallRye Reactive Messaging channel `orders`. Two consumers subscribe: order persistence + inventory, and low-stock warnings. To move to AMQ/Kafka, add the connector extension and set `mp.messaging.*` config.
- Carts are held in an application-scoped `CartStore` keyed by cartId. There is no HTTP session and no `<distributable/>`. Carts are in memory, so run 1 replica until a Data Grid/Redis CartStore exists.

## Container
```bash
podman build -f Containerfile -t coolstore .
podman compose up        # app + PostgreSQL 15 (compose.yaml)
```

## Deploy to OpenShift
```bash
oc new-project coolstore-dev
oc apply -k deploy/openshift/overlays/dev     # PostgreSQL 15, ImageStream, BuildConfig, Deployment, Service, Route, HPA
oc start-build coolstore --from-dir=. --follow
src/test/e2e/smoke.sh https://$(oc get route coolstore -o jsonpath='{.spec.host}')
```
Before applying, edit `deploy/openshift/base/secret.yaml`, which is a template. To roll back: `oc rollout undo deployment/coolstore`.

Migration artifacts: `mig-prompt-workspace/`, `mig-plan-workspace/`, `mig-execute-workspace/`, `migiq-workspace/`.
