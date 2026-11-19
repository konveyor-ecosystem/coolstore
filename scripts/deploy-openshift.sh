#!/usr/bin/env bash
#
# deploy-openshift.sh — mig-deploy automation for the coolstore monolith (APP_ID=7)
#
# Implements the mig-deploy skill workflow on top of the mig-containerize output:
#   Step 1  Verify prerequisites (oc login, containerization artifacts, rgctl index)
#   Step 2  Target namespace (created if missing)
#   Step 3  Detect application dependencies (PostgreSQL, Keycloak)
#   Step 4  Check existing dependencies in the cluster
#   Step 5  Deploy missing dependencies (generated YAML; --skip-deps for external)
#   Step 6  Build + push the container image (podman/docker -> OpenShift registry)
#   Step 7  Apply manifests in dependency-safe order
#           (Secret -> ConfigMap -> realm -> deps -> app -> Service -> Routes)
#   Step 8  Wait for the application rollout
#   Step 9  Comprehensive health verification
#           (pods, Service, Route, endpoints, dependency connectivity, Flyway)
#   Step 10 Deployment report
#
# The repo working tree is never modified: manifests are staged into a temp dir
# where the namespace and image reference are rewritten as needed.
#
# Usage:
#   ./scripts/deploy-openshift.sh [options]           deploy
#   ./scripts/deploy-openshift.sh rollback [options]  roll back the app deployment
#
# Options:
#   -n, --namespace NAME    Target namespace (default: coolstore)
#   -t, --image-tag TAG     Image tag (default: latest)
#   -i, --image REF         Full image reference; implies --skip-build
#       --skip-build        Skip image build+push (image must already exist in
#                           the target registry/namespace)
#       --skip-deps         Do not deploy PostgreSQL/Keycloak (assume external /
#                           already managed services)
#       --dry-run           Print the actions without executing them
#       --report-file PATH  Also write the deployment report to PATH
#   -h, --help              Show this help
#
# Environment overrides:
#   DB_USER / DB_PASSWORD       Database credentials. When DB_PASSWORD is set a
#                               Secret is generated from the environment;
#                               otherwise the placeholder Secret from
#                               openshift/02-secret.yaml is applied (DEV ONLY).
#   KEYCLOAK_ADMIN_PASSWORD     Replaces the Keycloak admin placeholder from
#                               openshift/04-keycloak.yaml.
#   COOLSTORE_IMAGE_REGISTRY    Override registry auto-detection.
#
# Examples:
#   DB_PASSWORD=s3cr3t ./scripts/deploy-openshift.sh
#   ./scripts/deploy-openshift.sh --skip-build --namespace coolstore-test
#   ./scripts/deploy-openshift.sh --dry-run
#   ./scripts/deploy-openshift.sh rollback
#
set -euo pipefail

# ---------------------------------------------------------------------------
# Constants
# ---------------------------------------------------------------------------
APP_NAME="coolstore"
APP_IMAGE_NAME="coolstore-monolith"
DEFAULT_NAMESPACE="coolstore"
MANIFEST_DIR="openshift"
REALM_FILE="realm-export.json"
POSTGRES_IMAGE_FILE="03-postgres.yaml"     # StatefulSet + headless Service
KEYCLOAK_IMAGE_FILE="04-keycloak.yaml"     # Deployment + Service
KEYCLOAK_ROUTE_FILE="08-keycloak-route.yaml"
APP_DEPLOYMENT_FILE="05-deployment.yaml"
APP_SERVICE_FILE="06-service.yaml"
APP_ROUTE_FILE="07-route.yaml"
SECRET_FILE="02-secret.yaml"
CONFIGMAP_FILE="01-configmap.yaml"
ROLLOUT_TIMEOUT="300s"
INTERNAL_REGISTRY="image-registry.openshift-image-registry.svc:5000"

# Code-derived smoke endpoints (fallback when no rgctl index is available):
#   /health.jsp      — webapp health page (probe target)
#   /                — AngularJS SPA
#   /services/...    — JAX-RS API (RestApplication @ApplicationPath("/services"))
SMOKE_PATHS=("/health.jsp" "/" "/services/products" "/services/orders")

# ---------------------------------------------------------------------------
# Helpers
# ---------------------------------------------------------------------------
REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$REPO_ROOT"

log()  { printf '[INFO]  %s\n' "$*"; }
warn() { printf '[WARN]  %s\n' "$*" >&2; WARNING_COUNT=$((WARNING_COUNT + 1)); }
die()  { printf '[ERROR] %s\n' "$*" >&2; exit 1; }
hr()   { printf -- '---\n'; }

# Execute a mutation (array form). In dry-run mode only print it.
run() {
  if [[ "$DRY_RUN" == "true" ]]; then
    printf '[dry-run] %s\n' "$*"
  else
    "$@"
  fi
}

# Execute a mutation given as a shell string (for pipelines). Dry-run prints it.
runsh() {
  if [[ "$DRY_RUN" == "true" ]]; then
    printf '[dry-run] %s\n' "$1"
  else
    bash -c "$1"
  fi
}

# Query the cluster (stdout). In dry-run mode emits nothing and succeeds.
ocq() {
  if [[ "$DRY_RUN" == "true" ]]; then
    printf '[dry-run] (query) oc %s\n' "$*" >&2
    return 0
  fi
  oc "$@"
}

# Existence probes: in dry-run everything is treated as missing so the full
# plan (namespace creation, dependency deployment, ...) is previewed.
cluster_has() {
  if [[ "$DRY_RUN" == "true" ]]; then return 1; fi
  "$@" &>/dev/null
}

http_code() {
  local c
  c="$(curl -k -s -o /dev/null -w '%{http_code}' --max-time 10 "$1" 2>/dev/null || true)"
  echo "${c:-000}"
}

usage() { sed -n '2,/^set -euo/p' "$0" | sed '$d' | sed 's/^# \{0,1\}//'; }

# Rollback (invoked after the Step 1 prerequisite checks when the subcommand
# is 'rollback' — see the dispatch right after Step 1).
rollback() {
  step "Rollback"
  log "Rolling back deployment/$APP_NAME in namespace '$NAMESPACE'..."
  run oc rollout undo deployment/"$APP_NAME" -n "$NAMESPACE"
  run oc rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout="$ROLLOUT_TIMEOUT"
  log "Rollback complete. Diagnostics if needed:"
  log "  oc logs -f deployment/$APP_NAME -n $NAMESPACE"
  log "  oc describe deployment/$APP_NAME -n $NAMESPACE"
}

# ---------------------------------------------------------------------------
# Argument parsing
# ---------------------------------------------------------------------------
NAMESPACE="$DEFAULT_NAMESPACE"
IMAGE_TAG="latest"
IMAGE_REF=""
SKIP_BUILD="false"
SKIP_DEPS="false"
DRY_RUN="false"
REPORT_FILE=""
WARNING_COUNT=0
COMMAND="deploy"

if [[ "${1:-}" == "rollback" ]]; then
  COMMAND="rollback"
  shift
fi

while [[ $# -gt 0 ]]; do
  case "$1" in
    -n|--namespace)   NAMESPACE="${2:-}"; shift 2 ;;
    -t|--image-tag)   IMAGE_TAG="${2:-}"; shift 2 ;;
    -i|--image)       IMAGE_REF="${2:-}"; SKIP_BUILD="true"; shift 2 ;;
    --skip-build)     SKIP_BUILD="true"; shift ;;
    --skip-deps)      SKIP_DEPS="true"; shift ;;
    --dry-run)        DRY_RUN="true"; shift ;;
    --report-file)    REPORT_FILE="${2:-}"; shift 2 ;;
    -h|--help)        usage; exit 0 ;;
    *) die "Unknown option: $1 (see --help)" ;;
  esac
done

[[ "$NAMESPACE" != */* && -n "$NAMESPACE" ]] || die "Invalid namespace: '$NAMESPACE'"

# ---------------------------------------------------------------------------
# Step 1 — Verify prerequisites and cluster access
# ---------------------------------------------------------------------------
step() { printf '\n=== %s ===\n' "$1"; }

step "Step 1: prerequisites"

for f in "Dockerfile" \
         "$MANIFEST_DIR/$SECRET_FILE" \
         "$MANIFEST_DIR/$CONFIGMAP_FILE" \
         "$MANIFEST_DIR/$POSTGRES_IMAGE_FILE" \
         "$MANIFEST_DIR/$KEYCLOAK_IMAGE_FILE" \
         "$MANIFEST_DIR/$APP_DEPLOYMENT_FILE" \
         "$MANIFEST_DIR/$APP_SERVICE_FILE" \
         "$MANIFEST_DIR/$APP_ROUTE_FILE" \
         "$MANIFEST_DIR/$KEYCLOAK_ROUTE_FILE" \
         "scripts/entrypoint.sh" \
         "scripts/wildfly-config.cli" \
         "$REALM_FILE"; do
  [[ -f "$f" ]] || die "Containerization artifact missing: $f (run mig-containerize first)"
done
log "Containerization artifacts found (Dockerfile, $MANIFEST_DIR/, scripts/, $REALM_FILE)"

CLUSTER="(dry-run)"
OC_USER="(dry-run)"
if [[ "$DRY_RUN" == "true" ]]; then
  warn "dry-run mode: cluster access is not verified"
else
  command -v oc &>/dev/null \
    || die "oc CLI not found. Install it, then: oc login <cluster-url>"
  OC_USER="$(oc whoami 2>/dev/null)" \
    || die "Not logged into an OpenShift cluster. Run: oc login <cluster-url>"
  CLUSTER="$(oc whoami --show-server)"
  log "Logged into OpenShift as: $OC_USER"
  log "Cluster: $CLUSTER"
fi

# Subcommand dispatch: rollback short-circuits the deploy flow here, after the
# prerequisite checks above have confirmed cluster access.
if [[ "$COMMAND" == "rollback" ]]; then
  rollback
  exit 0
fi

# rgctl index (optional) — enables graph-driven endpoint discovery in Step 9.
HAS_GRAPH="false"
if rgctl -f json metrics --pagerank &>/dev/null; then
  HAS_GRAPH="true"
  log "rgctl index ready — will use it for endpoint testing"
else
  log "No rgctl index — endpoint testing falls back to code-derived endpoints"
fi

# ---------------------------------------------------------------------------
# Step 2 — Target namespace
# ---------------------------------------------------------------------------
step "Step 2: namespace"

if cluster_has oc get namespace "$NAMESPACE"; then
  log "Namespace '$NAMESPACE' exists"
else
  log "Namespace '$NAMESPACE' does not exist"
  if run oc new-project "$NAMESPACE"; then
    log "Created namespace '$NAMESPACE'"
  else
    die "Could not create namespace '$NAMESPACE' (quota/permissions?) — create it and re-run"
  fi
fi

# ---------------------------------------------------------------------------
# Step 3 — Detect application dependencies (mig-containerize output)
# ---------------------------------------------------------------------------
step "Step 3: dependency detection"

# name:manifest:wait-type  (wait-type: statefulset|deployment)
APP_DEPS=(
  "postgres:$MANIFEST_DIR/$POSTGRES_IMAGE_FILE:statefulset"
  "keycloak:$MANIFEST_DIR/$KEYCLOAK_IMAGE_FILE:deployment"
)

# Generic scan for any additional dependency manifests the skill knows about.
EXTRA_DEP_PATTERNS=('*mysql*.yaml' '*mongodb*.yaml' '*oracle*.yaml' '*kafka*.yaml'
                    '*rabbitmq*.yaml' '*activemq*.yaml' '*redis*.yaml' '*memcached*.yaml')
for pattern in "${EXTRA_DEP_PATTERNS[@]}"; do
  for file in "$MANIFEST_DIR"/$pattern; do
    [[ -e "$file" ]] || continue
    [[ "$file" == *"-route.yaml" || "$file" == *"-service.yaml" ]] && continue
    name="$(basename "$file" .yaml | sed 's/-deployment$//;s/-statefulset$//')"
    APP_DEPS+=("$name:$file:deployment")
    warn "additional dependency manifest detected: $file"
  done
done

log "Detected dependencies:"
for dep in "${APP_DEPS[@]}"; do
  log "  - ${dep%%:*} (${dep#*:})"
done

# ---------------------------------------------------------------------------
# Steps 4+5 — Check existing dependencies, deploy missing ones
# (applied later, in Step 7, in dependency-safe order — detection first)
# ---------------------------------------------------------------------------
step "Step 4/5: dependency state"

declare -a DEPLOY_DEP_MANIFESTS=()
for entry in "${APP_DEPS[@]}"; do
  dep="${entry%%:*}"; rest="${entry#*:}"
  manifest="${rest%%:*}"; kind="${rest#*:}"
  if cluster_has oc get "$kind" "$dep" -n "$NAMESPACE"; then
    log "Found existing: $dep (${kind}) — will not redeploy"
  elif [[ "$SKIP_DEPS" == "true" ]]; then
    log "Skipping $dep (--skip-deps) — assuming an external/managed service"
    warn "$dep not deployed; configure connection details in coolstore-secrets/coolstore-config if external"
  else
    log "Missing: $dep — will deploy from $manifest"
    DEPLOY_DEP_MANIFESTS+=("$entry")
  fi
done

# ---------------------------------------------------------------------------
# Step 6 — Build and push the container image
# ---------------------------------------------------------------------------
step "Step 6: image build and push"

if [[ "$SKIP_BUILD" == "true" && -z "$IMAGE_REF" ]]; then
  log "--skip-build: the image must already exist in the target registry"
fi

# Registry: default route of the integrated registry, else in-cluster service.
if [[ -n "${COOLSTORE_IMAGE_REGISTRY:-}" ]]; then
  REGISTRY="$COOLSTORE_IMAGE_REGISTRY"
  log "Using registry (COOLSTORE_IMAGE_REGISTRY): $REGISTRY"
else
  REGISTRY="$(ocq get route default-route -n openshift-image-registry \
              -o jsonpath='{.spec.host}' 2>/dev/null || true)"
  if [[ -z "$REGISTRY" ]]; then
    REGISTRY="$INTERNAL_REGISTRY"
    warn "default-route not found for the integrated registry; using $REGISTRY" \
         "(pushable from inside the cluster, or enable the route with:" \
         "oc patch configs.imageregistry.operator.openshift.io/cluster" \
         "--patch '{\"spec\":{\"defaultRoute\":true}}' --type=merge)"
  else
    log "Using integrated registry route: $REGISTRY"
  fi
fi

if [[ -z "$IMAGE_REF" ]]; then
  IMAGE_REF="$REGISTRY/$NAMESPACE/$APP_IMAGE_NAME:$IMAGE_TAG"
fi
log "Image reference: $IMAGE_REF"

if [[ "$SKIP_BUILD" != "true" ]]; then
  if command -v podman &>/dev/null; then
    BUILD_CMD="podman"
  elif command -v docker &>/dev/null; then
    BUILD_CMD="docker"
  elif [[ "$DRY_RUN" == "true" ]]; then
    BUILD_CMD="podman"   # display-only in dry-run; not executed
    warn "dry-run: no podman/docker on this machine — build commands are printed, not executed"
  else
    die "Neither podman nor docker found — install one, or pass --skip-build/--image"
  fi
  log "Building with $BUILD_CMD..."
  run "$BUILD_CMD" build -t "$IMAGE_REF" -f Dockerfile .
  log "Image built successfully"

  log "Authenticating to the registry..."
  if ! run oc registry login; then
    log "oc registry login unavailable — falling back to token login"
    runsh "$BUILD_CMD login -u \"\$(oc whoami)\" -p \"\$(oc whoami -t)\" $REGISTRY"
  fi
  run "$BUILD_CMD" push "$IMAGE_REF"
  log "Image pushed successfully"
fi

# ---------------------------------------------------------------------------
# Step 7 — Apply manifests in dependency-safe order
# ---------------------------------------------------------------------------
step "Step 7: deploy manifests"

# Stage manifests into a temp dir (never modify the repo working tree).
STAGE="$(mktemp -d /tmp/coolstore-deploy.XXXXXX)"
trap 'rm -rf "$STAGE"' EXIT
cp "$MANIFEST_DIR"/*.yaml "$STAGE/"
if [[ "$NAMESPACE" != "$DEFAULT_NAMESPACE" ]]; then
  sed -i "s/namespace: $DEFAULT_NAMESPACE/namespace: $NAMESPACE/g" "$STAGE"/*.yaml
  log "Staged manifests rewritten for namespace '$NAMESPACE'"
fi

# 7a. Secret — generated from the environment when DB_PASSWORD is provided,
#     otherwise the placeholder Secret is applied with a loud warning.
if [[ -n "${DB_PASSWORD:-}" ]]; then
  esc() { sed 's/"/\\"/g' <<<"$1"; }
  {
    printf '# Generated by deploy-openshift.sh from DB_USER/DB_PASSWORD\n'
    printf 'apiVersion: v1\nkind: Secret\nmetadata:\n  name: coolstore-secrets\n'
    printf '  namespace: %s\n  labels:\n    app: coolstore\ntype: Opaque\nstringData:\n' "$NAMESPACE"
    printf '  DB_USER: "%s"\n' "$(esc "${DB_USER:-coolstore}")"
    printf '  DB_PASSWORD: "%s"\n' "$(esc "$DB_PASSWORD")"
  } > "$STAGE/$SECRET_FILE"
  log "Generated $SECRET_FILE from DB_USER/DB_PASSWORD environment"
else
  warn "DB_PASSWORD not set — applying the placeholder Secret from $SECRET_FILE (DEV ONLY)"
fi

# 7b. Keycloak admin password (placeholder in 04-keycloak.yaml).
if [[ -n "${KEYCLOAK_ADMIN_PASSWORD:-}" ]]; then
  esc_pw="${KEYCLOAK_ADMIN_PASSWORD//\\/\\\\}"   # escape backslashes
  esc_pw="${esc_pw//&/\\&}"                      # escape sed metacharacters
  esc_pw="${esc_pw//|/\\|}"
  sed -i "s|CHANGE_ME_admin_password|$esc_pw|g" "$STAGE/$KEYCLOAK_IMAGE_FILE"
  log "Keycloak admin password set from KEYCLOAK_ADMIN_PASSWORD"
else
  warn "KEYCLOAK_ADMIN_PASSWORD not set — Keycloak admin uses the manifest placeholder (DEV ONLY)"
fi

# 7c. Secrets first (skill order: Secrets -> ConfigMaps -> PVCs -> deps -> app -> routes).
run oc apply -f "$STAGE/$SECRET_FILE" -n "$NAMESPACE"
run oc apply -f "$STAGE/$CONFIGMAP_FILE" -n "$NAMESPACE"

# 7d. Keycloak realm ConfigMap (realm too large to inline in a manifest).
if cluster_has oc get configmap keycloak-realm -n "$NAMESPACE"; then
  log "ConfigMap keycloak-realm already exists"
else
  log "Creating ConfigMap keycloak-realm from $REALM_FILE..."
  runsh "oc create configmap keycloak-realm --from-file=$REALM_FILE -n $NAMESPACE \
         --dry-run=client -o yaml | oc apply -f -"
fi

# 7e. Dependencies (Step 5, option 1: deploy using generated YAML).
for entry in "${DEPLOY_DEP_MANIFESTS[@]}"; do
  dep="${entry%%:*}"; rest="${entry#*:}"
  manifest="$STAGE/$(basename "${rest%%:*}")"; kind="${rest#*:}"
  log "Deploying $dep from $(basename "$manifest")..."
  run oc apply -f "$manifest" -n "$NAMESPACE"
  log "Waiting for $dep to be ready..."
  if [[ "$kind" == "statefulset" ]]; then
    run oc wait --for=condition=Ready pod -l "app=$dep" -n "$NAMESPACE" --timeout="$ROLLOUT_TIMEOUT"
  else
    run oc rollout status "$kind/$dep" -n "$NAMESPACE" --timeout="$ROLLOUT_TIMEOUT"
  fi
  log "Deployed $dep successfully"
done

# 7f. Database migration job if one was generated (this app runs Flyway in-process
#     at startup — DataBaseMigrationStartup applies V1_1/V1_2 — so usually absent).
if [[ -f "database-migration-job.yaml" ]]; then
  log "Applying database-migration-job.yaml..."
  run oc apply -f database-migration-job.yaml -n "$NAMESPACE"
  run oc wait --for=condition=complete job/database-migration -n "$NAMESPACE" --timeout="$ROLLOUT_TIMEOUT"
else
  log "No database-migration-job.yaml — schema is migrated in-process by Flyway at startup"
fi

# 7g. Keycloak external Route — the SPA's browser-side OIDC adapter
#     (keycloak.json) must reach Keycloak via an externally routable URL, not
#     the in-cluster service DNS. Patch the ConfigMap accordingly.
if [[ "$SKIP_DEPS" != "true" ]]; then
  if cluster_has oc get route keycloak -n "$NAMESPACE"; then
    log "Route keycloak already exists"
  else
    run oc apply -f "$STAGE/$KEYCLOAK_ROUTE_FILE" -n "$NAMESPACE"
  fi
  KC_HOST="$(ocq get route keycloak -n "$NAMESPACE" -o jsonpath='{.spec.host}' 2>/dev/null || true)"
  if [[ -n "$KC_HOST" ]]; then
    log "Keycloak external URL: https://$KC_HOST/"
    runsh "oc patch configmap coolstore-config -n $NAMESPACE \
           -p '{\"data\":{\"KEYCLOAK_AUTH_SERVER_URL\":\"https://$KC_HOST/\"}}'"
    log "Patched KEYCLOAK_AUTH_SERVER_URL in coolstore-config"
  else
    warn "Could not determine the keycloak Route host — KEYCLOAK_AUTH_SERVER_URL left unchanged"
  fi
else
  KC_HOST="$(ocq get route keycloak -n "$NAMESPACE" -o jsonpath='{.spec.host}' 2>/dev/null || true)"
  if [[ -n "$KC_HOST" ]]; then
    runsh "oc patch configmap coolstore-config -n $NAMESPACE \
           -p '{\"data\":{\"KEYCLOAK_AUTH_SERVER_URL\":\"https://$KC_HOST/\"}}'"
    log "Patched KEYCLOAK_AUTH_SERVER_URL from the existing keycloak Route"
  else
    warn "--skip-deps and no keycloak Route found — KEYCLOAK_AUTH_SERVER_URL left unchanged"
  fi
fi

# 7h. Application deployment, Service and Route.
#     Rewrite the image reference in the staged manifest (never the repo file).
sed -i "s|^        image: .*|        image: $IMAGE_REF|" "$STAGE/$APP_DEPLOYMENT_FILE"

# envFrom ConfigMaps do not propagate to running pods: if this is a re-deploy,
# restart so the (possibly patched) KEYCLOAK_AUTH_SERVER_URL is picked up.
if cluster_has oc get deployment "$APP_NAME" -n "$NAMESPACE"; then
  log "Deployment $APP_NAME already exists — restarting to pick up config changes"
  run oc rollout restart deployment/"$APP_NAME" -n "$NAMESPACE"
fi

run oc apply -f "$STAGE/$APP_DEPLOYMENT_FILE" -n "$NAMESPACE"
run oc apply -f "$STAGE/$APP_SERVICE_FILE" -n "$NAMESPACE"
run oc apply -f "$STAGE/$APP_ROUTE_FILE" -n "$NAMESPACE"
log "Application manifests deployed"

# ---------------------------------------------------------------------------
# Step 8 — Wait for the deployment to be ready
# ---------------------------------------------------------------------------
step "Step 8: rollout"

if ! run oc rollout status deployment/"$APP_NAME" -n "$NAMESPACE" --timeout="$ROLLOUT_TIMEOUT"; then
  log "Deployment failed to roll out. Diagnostics:"
  run oc get pods -n "$NAMESPACE" -l "app=$APP_NAME" || true
  run oc logs -n "$NAMESPACE" -l "app=$APP_NAME" --tail=50 || true
  die "Rollout failed — fix the issue and re-run, or roll back: ./scripts/deploy-openshift.sh rollback"
fi
log "Deployment rolled out successfully"

# ---------------------------------------------------------------------------
# Step 9 — Comprehensive health verification
# ---------------------------------------------------------------------------
step "Step 9: health verification"

# 9.1 Pod status
POD_NAMES="$(ocq get pods -n "$NAMESPACE" -l "app=$APP_NAME" -o name \
             | cut -d/ -f2 | tr '\n' ' ')"
if [[ -z "${POD_NAMES// /}" && "$DRY_RUN" != "true" ]]; then
  die "No pods found for $APP_NAME"
fi
POD_PHASES="$(ocq get pods -n "$NAMESPACE" -l "app=$APP_NAME" \
              -o jsonpath='{.items[*].status.phase}')"
log "Pods: ${POD_NAMES:-<dry-run>}"
log "Phases: ${POD_PHASES:-<dry-run>}"
if [[ "$DRY_RUN" != "true" ]]; then
  for phase in $POD_PHASES; do
    [[ "$phase" == "Running" ]] || die "Pod phase is '$phase' (expected Running)"
  done
  log "All $APP_NAME pods are Running"
fi

# 9.2 Service
SVC_IP="$(ocq get service "$APP_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.clusterIP}')"
SVC_PORT="$(ocq get service "$APP_NAME" -n "$NAMESPACE" \
            -o jsonpath='{.spec.ports[0].port}')"
log "Service $APP_NAME: ${SVC_IP:-<dry-run>}:${SVC_PORT:-8080}"

# 9.3 Route
ROUTE_HOST="$(ocq get route "$APP_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.host}')"
ROUTE_URL="https://${ROUTE_HOST:-<route-host>}"
log "Route URL: $ROUTE_URL"
if [[ "$DRY_RUN" != "true" ]]; then
  CODE="$(http_code "$ROUTE_URL/health.jsp")"
  if [[ "$CODE" == "200" ]]; then
    log "Health endpoint /health.jsp accessible via Route (HTTP $CODE)"
  elif [[ "$CODE" == "000" ]]; then
    warn "Route not reachable from this machine (HTTP $CODE) — may be network policy; verify in-cluster"
  else
    warn "Route returned HTTP $CODE for /health.jsp"
  fi
fi

# 9.4 Endpoint testing — rgctl graph-driven when an index exists, otherwise
#     the code-derived SMOKE_PATHS above.
declare -a TEST_PATHS=("${SMOKE_PATHS[@]}")
if [[ "$HAS_GRAPH" == "true" ]]; then
  log "Deriving endpoints from the rgctl graph..."
  GRAPH_ENDPOINTS="$(rgctl -f json gql \
    "MATCH (n:Function) WHERE n.name LIKE '*Endpoint' RETURN n" 2>/dev/null \
    | grep -oE '"name": *"[A-Za-z]+Endpoint"' | grep -oE '[A-Za-z]+Endpoint' \
    | sort -u || true)"
  for ep in $GRAPH_ENDPOINTS; do
    path="/services/$(echo "$ep" | sed 's/Endpoint$//' | tr '[:upper:]' '[:lower:]')"
    # de-duplicate against the static list
    if ! printf '%s\n' "${TEST_PATHS[@]}" | grep -qx "$path"; then
      TEST_PATHS+=("$path")
    fi
  done
fi
if [[ "$DRY_RUN" != "true" ]]; then
  log "Testing endpoints via the Route:"
  for path in "${TEST_PATHS[@]}"; do
    CODE="$(http_code "$ROUTE_URL$path")"
    case "$CODE" in
      200|301|302) log "  ✓ $path (HTTP $CODE)" ;;
      401|403)     log "  ✓ $path (HTTP $CODE — protected, expected)" ;;
      *)           warn "  $path returned HTTP $CODE" ;;
    esac
  done
else
  log "Would test endpoints: ${TEST_PATHS[*]}"
fi

# 9.5 Dependency connectivity from the application pod
APP_POD="$(ocq get pods -n "$NAMESPACE" -l "app=$APP_NAME" -o jsonpath='{.items[0].metadata.name}' 2>/dev/null || true)"
if [[ -n "$APP_POD" && "$DRY_RUN" != "true" ]]; then
  for dep in "${APP_DEPS[@]}"; do
    dep_name="${dep%%:*}"
    dep_host="$dep_name"
    dep_port="$(ocq get service "$dep_name" -n "$NAMESPACE" \
                -o jsonpath='{.spec.ports[0].port}' 2>/dev/null || echo "")"
    [[ -n "$dep_port" ]] || { warn "Service not found for $dep_name"; continue; }
    log "Checking connectivity to $dep_name ($dep_host:$dep_port)..."
    CONN="$(oc exec "$APP_POD" -n "$NAMESPACE" -- bash -c \
            "timeout 5 bash -c '</dev/tcp/$dep_host/$dep_port' 2>/dev/null && echo connected || echo failed" \
            || echo failed)"
    if [[ "$CONN" == "connected" ]]; then
      log "  ✓ Application can connect to $dep_name"
    else
      warn "  Application cannot connect to $dep_name"
    fi
  done
else
  log "Dependency connectivity: checked in-cluster after rollout (skipped in dry-run)"
fi

# 9.6 Flyway migration evidence (in-process schema migration on CoolstoreDS)
if [[ "$DRY_RUN" != "true" ]]; then
  FLYWAY_LINES="$(oc logs deployment/"$APP_NAME" -n "$NAMESPACE" --tail=500 2>/dev/null \
                  | grep -iE 'flyway|successfully applied|V1_[0-9]' | head -5 || true)"
  if [[ -n "$FLYWAY_LINES" ]]; then
    log "Flyway migration evidence:"
    while IFS= read -r line; do log "  $line"; done <<<"$FLYWAY_LINES"
  else
    warn "No Flyway log lines found in the last 500 — verify schema manually"
  fi
fi

# ---------------------------------------------------------------------------
# Step 10 — Deployment report
# ---------------------------------------------------------------------------
step "Step 10: deployment report"

READY_REPLICAS="$(ocq get deployment "$APP_NAME" -n "$NAMESPACE" \
                  -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
SPEC_REPLICAS="$(ocq get deployment "$APP_NAME" -n "$NAMESPACE" \
                 -o jsonpath='{.spec.replicas}' 2>/dev/null || true)"
DEP_IMAGE="$(ocq get deployment "$APP_NAME" -n "$NAMESPACE" \
             -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || true)"

report() {
  cat <<EOF
=========================================
  DEPLOYMENT REPORT — $APP_NAME
=========================================
Cluster:       $CLUSTER
Namespace:     $NAMESPACE
Deployed by:   $OC_USER
Timestamp:     $(date -u '+%Y-%m-%d %H:%M:%S UTC')

--- Application ---
Deployment:    $APP_NAME
Image:         ${DEP_IMAGE:-$IMAGE_REF}
Replicas:      ${SPEC_REPLICAS:-2} desired / ${READY_REPLICAS:-?} ready
Route:         $ROUTE_URL
Health:        $ROUTE_URL/health.jsp
API:           $ROUTE_URL/services/products

--- Dependencies ---
EOF
  for entry in "${APP_DEPS[@]}"; do
    dep_name="${entry%%:*}"
    dep_status="$(ocq get deployment "$dep_name" -n "$NAMESPACE" \
                  -o jsonpath='{.status.conditions[?(@.type=="Available")].status}' 2>/dev/null || true)"
    if [[ -z "$dep_status" ]]; then
      dep_status="$(ocq get statefulset "$dep_name" -n "$NAMESPACE" \
                    -o jsonpath='{.status.readyReplicas}' 2>/dev/null || true)"
      [[ -n "$dep_status" ]] && dep_status="readyReplicas=$dep_status"
    fi
    printf '  %s: %s\n' "$dep_name" "${dep_status:-n/a (external or not deployed)}"
  done
  cat <<EOF

--- Pods ---
EOF
  ocq get pods -n "$NAMESPACE" -o wide || true
  cat <<EOF

--- Recent events ---
EOF
  ocq get events -n "$NAMESPACE" --sort-by='.lastTimestamp' 2>/dev/null | tail -10 || true
  cat <<EOF

=========================================
  DEPLOYMENT COMPLETE ($WARNING_COUNT warning(s))
=========================================

Next steps:
  - Monitor logs:      oc logs -f deployment/$APP_NAME -n $NAMESPACE
  - Check metrics:     oc adm top pods -n $NAMESPACE
  - View details:      oc describe deployment/$APP_NAME -n $NAMESPACE
  - Roll back:         ./scripts/deploy-openshift.sh rollback
EOF
}

if [[ -n "$REPORT_FILE" ]]; then
  report | tee "$REPORT_FILE"
else
  report
fi

log "Done."
