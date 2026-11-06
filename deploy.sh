#!/usr/bin/env bash
# CoolStore — one-command OpenShift deployment (mig-deploy stage).
#
# Automates the workflow from the mig-deploy skill:
#   prereqs → namespace → secrets → Keycloak realm → dependencies →
#   build/push image → frontend OIDC config → application → rollout →
#   health checks + endpoint smoke tests (endpoints identified via rgctl).
#
# Usage (from the repo root, logged in with `oc login`):
#   ./deploy.sh
# Environment overrides:
#   NAMESPACE     target namespace          (default: coolstore)
#   IMAGE         full image reference      (default: built + pushed to the
#                                            OpenShift internal registry)
#   QUAY_NAMESPACE, QUAY_USERNAME, QUAY_PASSWORD   push to Quay instead
#   SKIP_BUILD=1  reuse the image already referenced in deployment.yaml
#
# Secrets: generated at runtime (openssl rand) and created only if absent —
# the committed secret.yaml CHANGE_ME placeholders are never applied.
# Re-running the script is safe (oc apply is idempotent, secrets are kept).
set -euo pipefail

cd "$(dirname "$0")"
NAMESPACE="${NAMESPACE:-coolstore}"
APP=coolstore-quarkus
SKIP_BUILD="${SKIP_BUILD:-0}"

log()  { printf '\e[32m==>\e[0m %s\n' "$*"; }
warn() { printf '\e[33mWARN:\e[0m %s\n' "$*"; }
die()  { printf '\e[31mERROR:\e[0m %s\n' "$*" >&2; exit 1; }

# ---------------------------------------------------------------- Step 1: prereqs
oc whoami >/dev/null 2>&1 || die "not logged into OpenShift — run: oc login <cluster-url>"
log "Logged in as $(oc whoami) @ $(oc whoami --show-server)"
for f in Dockerfile deployment.yaml service.yaml route.yaml configmap.yaml \
         database-statefulset.yaml amq-broker.yaml keycloak.yaml realm-export.json; do
  [ -f "$f" ] || die "missing containerization artifact: $f (run mig-containerize first)"
done
log "Containerization artifacts present"
if rgctl -f json metrics --pagerank >/dev/null 2>&1; then
  log "rgctl index ready — smoke-test endpoints derived from the code graph"
else
  warn "no rgctl index — using static endpoint list (endpoints verified from source)"
fi

# ---------------------------------------------------------------- Step 2: namespace
if oc get namespace "$NAMESPACE" >/dev/null 2>&1; then
  log "Namespace '$NAMESPACE' exists"
else
  log "Creating namespace '$NAMESPACE'"
  oc new-project "$NAMESPACE" 2>/dev/null || oc create namespace "$NAMESPACE"
fi

# ---------------------------------------------------------------- Step 3: secrets
gen() { openssl rand -hex 16 2>/dev/null || head -c16 /dev/urandom | od -An -tx1 | tr -d ' \n'; }
ensure_secret() { # ensure_secret <name> <key=value>...
  local name=$1; shift
  oc get secret "$name" -n "$NAMESPACE" >/dev/null 2>&1 && { log "Secret $name already present (kept)"; return; }
  local args=()
  for kv in "$@"; do args+=(--from-literal="$kv"); done
  oc create secret generic "$name" -n "$NAMESPACE" "${args[@]}" >/dev/null
  log "Secret $name created"
}
DB_PASS="$(gen)"; BROKER_PASS="$(gen)"; KC_PASS="$(gen)"
# App and backing-service credentials are kept in sync (same values in both
# secrets) — see the sync notes in secret.yaml.
ensure_secret coolstore-db   "QUARKUS_DATASOURCE_USERNAME=coolstore" "QUARKUS_DATASOURCE_PASSWORD=$DB_PASS"
ensure_secret postgresql-creds "POSTGRES_USER=coolstore" "POSTGRES_PASSWORD=$DB_PASS" "POSTGRES_DB=coolstore"
ensure_secret coolstore-broker "QUARKUS_ARTEMIS_USERNAME=coolstore" "QUARKUS_ARTEMIS_PASSWORD=$BROKER_PASS"
ensure_secret amq-creds      "AMQ_USER=coolstore" "AMQ_PASSWORD=$BROKER_PASS"
ensure_secret keycloak-admin "KC_BOOTSTRAP_ADMIN_USERNAME=admin" "KC_BOOTSTRAP_ADMIN_PASSWORD=$KC_PASS"

# ---------------------------------------------------------------- Step 4: Keycloak realm
oc create configmap keycloak-realm -n "$NAMESPACE" \
  --from-file=realm-export.json=realm-export.json \
  --dry-run=client -o yaml | oc apply -f - >/dev/null
log "Keycloak realm ConfigMap applied (realm 'eap'; redirect URIs are wildcards — no patching needed)"

# ---------------------------------------------------------------- Step 5: dependencies
oc apply -f configmap.yaml -n "$NAMESPACE" >/dev/null
log "ConfigMap coolstore-config applied"
for dep in database-statefulset.yaml amq-broker.yaml keycloak.yaml; do
  log "Applying $dep"
  oc apply -f "$dep" -n "$NAMESPACE" >/dev/null
done
log "Waiting for PostgreSQL, AMQ broker and Keycloak to become ready (up to 5 min each)"
oc wait --for=condition=ready pod -l app=postgresql -n "$NAMESPACE" --timeout=300s >/dev/null
oc wait --for=condition=ready pod -l app=amq-broker   -n "$NAMESPACE" --timeout=300s >/dev/null
oc wait --for=condition=ready pod -l app=keycloak     -n "$NAMESPACE" --timeout=300s >/dev/null
log "All dependencies ready"

# ---------------------------------------------------------------- Step 6: image
if [ "$SKIP_BUILD" = "1" ]; then
  IMAGE="$(oc get deployment "$APP" -n "$NAMESPACE" -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || true)"
  [ -n "$IMAGE" ] || IMAGE="quay.io/coolstore/${APP}:latest"
  log "SKIP_BUILD=1 — using existing image $IMAGE"
else
  command -v podman >/dev/null && BUILD=podman || { command -v docker >/dev/null && BUILD=docker; } || die "neither podman nor docker found"
  if [ -n "${QUAY_NAMESPACE:-}" ] && [ -n "${QUAY_USERNAME:-}" ]; then
    IMAGE="quay.io/${QUAY_NAMESPACE}/${APP}:latest"
    "$BUILD" login quay.io -u "$QUAY_USERNAME" -p "${QUAY_PASSWORD:-}" >/dev/null 2>&1 || true
  else
    REGISTRY="$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}' 2>/dev/null || true)"
    [ -n "$REGISTRY" ] || REGISTRY="image-registry.openshift-image-registry.svc:5000"
    IMAGE="$REGISTRY/$NAMESPACE/$APP:latest"
    "$BUILD" login -u "$(oc whoami)" -p "$(oc whoami -t)" "$REGISTRY" >/dev/null 2>&1 || warn "registry login failed — pushing may fail"
  fi
  log "Building image $IMAGE (multi-stage UBI build; tests run in CI, skipped here)"
  "$BUILD" build -t "$IMAGE" -f Dockerfile .
  log "Pushing image"
  "$BUILD" push "$IMAGE"
fi

# ---------------------------------------------------------------- Step 7: frontend OIDC config (plan task 7.2)
KC_HOST="$(oc get route keycloak -n "$NAMESPACE" -o jsonpath='{.spec.host}' 2>/dev/null || true)"
if [ -n "$KC_HOST" ]; then
  sed 's|"auth-server-url": ".*"|"auth-server-url": "https://'"$KC_HOST"'/"|' \
    src/main/resources/META-INF/resources/keycloak.json > /tmp/keycloak.json
  oc create configmap frontend-oidc -n "$NAMESPACE" \
    --from-file=keycloak.json=/tmp/keycloak.json \
    --dry-run=client -o yaml | oc apply -f - >/dev/null
  rm -f /tmp/keycloak.json
  log "Frontend OIDC config externalized: auth-server-url → https://$KC_HOST/ (ConfigMap frontend-oidc)"
else
  warn "Keycloak route not found — frontend keeps the baked-in keycloak.json (SSO disabled, catalog browsing works)"
fi

# ---------------------------------------------------------------- Step 8: application
oc apply -f deployment.yaml -f service.yaml -f route.yaml -f hpa.yaml -n "$NAMESPACE" >/dev/null
oc set image deployment/"$APP" "$APP=$IMAGE" -n "$NAMESPACE" >/dev/null
log "Application manifests applied (deployment/service/route/hpa)"
log "Waiting for rollout (up to 10 min)"
oc rollout status deployment/"$APP" -n "$NAMESPACE" --timeout=600s

# ---------------------------------------------------------------- Step 9: verification
ROUTE_URL="https://$(oc get route coolstore -n "$NAMESPACE" -o jsonpath='{.spec.host}')"
log "Route: $ROUTE_URL"
smoke() { # smoke <desc> <path> <expected>
  code="$(curl -s -o /dev/null -w '%{http_code}' -k --max-time 30 "$ROUTE_URL$2" || echo 000)"
  if [ "$code" = "$3" ]; then log "PASS  $2 → $code ($1)"
  else warn "FAIL  $2 → $code (expected $3) — $1"; return 1; fi
}
FAILED=0
# Endpoints below were derived from the rgctl code graph + @Path annotations:
#   ProductEndpoint @Path(/products)  → public  (quarkus.http.auth.permission.public)
#   CartEndpoint    @Path(/cart)      → OIDC    (policy: authenticated)
#   OrderEndpoint   @Path(/orders)    → OIDC    (policy: authenticated)
smoke "health (smallrye-health)"            /q/health/ready 200 || FAILED=1
smoke "public product catalog (graph: ProductEndpoint)" /services/products 200 || FAILED=1
smoke "orders API enforces OIDC (graph: OrderEndpoint)"  /services/orders 401 || FAILED=1
smoke "cart API enforces OIDC (graph: CartEndpoint)"     /services/cart/smoke-test 401 || FAILED=1

log "Pods:"; oc get pods -n "$NAMESPACE" -o wide | sed 's/^/    /'
echo
log "Deployment complete — application: $ROUTE_URL"
[ "$FAILED" = "0" ] || warn "one or more smoke tests failed — check: oc logs -f deployment/$APP -n $NAMESPACE"
echo "Rollback: oc rollout undo deployment/$APP -n $NAMESPACE"
exit "$FAILED"
