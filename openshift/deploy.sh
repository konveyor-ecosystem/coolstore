#!/usr/bin/env bash
# =============================================================================
# CoolStore monolith — OpenShift deployment automation (mig-deploy skill)
#
# Deploys the mig-containerize output to OpenShift with dependency management
# (PostgreSQL, Keycloak, Artemis broker), safe manifest ordering, image
# build/push, comprehensive health verification, and a deployment report.
#
# Usage:
#   ./openshift/deploy.sh                          # full deploy to "coolstore" ns
#   ./openshift/deploy.sh -n my-ns --create-ns     # full deploy to a new namespace
#   ./openshift/deploy.sh --dry-run                # print the plan, touch nothing
#   ./openshift/deploy.sh --verify-only            # health checks only
#   ./openshift/deploy.sh --rollback               # rollout undo
#
# Prerequisites: oc (logged in), podman or docker (unless --skip-build),
# Containerfile + openshift/*.yaml from mig-containerize (present in this repo).
# Optional: rgctl index for graph-driven endpoint testing (falls back to the
# source-derived endpoint list below).
# =============================================================================
set -euo pipefail

# ----------------------------------------------------------------------------
# Configuration defaults (override via flags or environment)
# ----------------------------------------------------------------------------
NAMESPACE="${NAMESPACE:-coolstore}"
APP_NAME="coolstore-monolith"
IMAGE_TAG="latest"
REGISTRY=""                    # auto-detected: OpenShift internal registry
DEP_STRATEGY="yaml"            # yaml | helm | operator | skip
HELM_CHARTS=""                 # dep=chart pairs for --dep-strategy helm
SKIP_BUILD=false
SKIP_SMOKE=false
ALLOW_EXAMPLE_SECRETS=false    # dev/test convenience only
SECRET_FILE=""                 # real secret manifest (avoids the example one)
CREATE_NS=false
DRY_RUN=false
VERIFY_ONLY=false
DO_ROLLBACK=false
REPORT_FILE=""

# Source-derived REST endpoints of the Quarkus app (used when no rgctl index):
#   METHOD PATH expected-codes   — 200/401/403 all mean "endpoint reachable";
#   401/403 is the correct result for OIDC-secured paths without a token.
FALLBACK_ENDPOINT_TESTS=(
  "GET /q/health/live 200"
  "GET /q/health/ready 200"
  "GET /services/products 200"
  "GET /services/cart/deploy-probe 200"
  "GET /services/orders 401,403"
  "POST /services/cart/checkout/deploy-probe 401,403"
)

# Dependencies detected from mig-containerize output (Step 3 of the workflow):
#   name -> manifest, workload kind, pod label, service:port
declare -A DEP_MANIFEST=(
  [postgresql]="openshift/postgresql.yaml"
  [keycloak]="openshift/keycloak.yaml"
  [broker]="openshift/broker.yaml"
)
declare -A DEP_KIND=(
  [postgresql]="statefulset"
  [keycloak]="deployment"
  [broker]="deployment"
)
declare -A DEP_LABEL=(
  [postgresql]="app=postgresql"
  [keycloak]="app=keycloak"
  [broker]="app=broker"
)
declare -A DEP_SVC=(
  [postgresql]="postgresql:5432"
  [keycloak]="keycloak:80"
  [broker]="broker:5672"
)

# ----------------------------------------------------------------------------
# Logging helpers
# ----------------------------------------------------------------------------
log()  { printf '%s\n' "$*"; }
ok()   { printf '✓ %s\n' "$*"; }
warn() { printf '⚠ %s\n' "$*" >&2; }
die()  { printf '✗ ERROR: %s\n' "$*" >&2; exit 1; }

usage() {
  # print the leading comment block (skipping the shebang), then the flags
  awk 'NR==1{next} /^#/{sub(/^# ?/, ""); print; next} {exit}' "$0"
  cat <<'EOF'

Flags:
  -n, --namespace NAME        Target namespace (default: coolstore, env NAMESPACE)
      --create-ns             Create the namespace if it does not exist
      --registry URL          Override the image registry (default: auto-detect
                              OpenShift internal registry)
      --image-tag TAG         Image tag to build/push (default: latest)
      --dep-strategy S        Missing-dependency handling: yaml (default),
                              helm, operator, or skip (assume external services)
      --helm-charts LIST      For helm strategy: dep=chart pairs, comma-separated
                              (e.g. postgresql=bitnami/postgresql)
      --secret-file FILE      Real Secret manifest (else reuse/create from
                              openshift/secret.example.yaml — CHANGE_ME guarded)
      --allow-example-secrets Apply the example Secret as-is (dev/test ONLY)
      --skip-build            Do not build/push; deploy the manifest image ref
      --skip-smoke            Skip the post-deploy smoke-test Job
      --verify-only           Only run health verification (no deploy)
      --rollback              oc rollout undo the application Deployment
      --dry-run               Print the full plan without touching the cluster
      --report-file FILE      Also write the deployment report to FILE
  -h, --help                  This help
EOF
}

# ----------------------------------------------------------------------------
# Command wrappers (dry-run aware)
# ----------------------------------------------------------------------------
apply_yaml() {  # apply_yaml FILE [sed-expr]
  local file="$1" expr="${2:-}"
  if $DRY_RUN; then
    validate_yaml "$file"
    if [ -n "$expr" ]; then
      log "  [dry-run] sed \"$expr\" $file | oc apply -f - -n $NAMESPACE"
    else
      log "  [dry-run] oc apply -f $file -n $NAMESPACE"
    fi
  elif [ -n "$expr" ]; then
    sed "$expr" "$file" | oc apply -f - -n "$NAMESPACE"
  else
    oc apply -f "$file" -n "$NAMESPACE"
  fi
}

validate_yaml() {  # syntax check when PyYAML is available (best effort)
  local file="$1"
  if command -v python3 >/dev/null 2>&1 && python3 -c 'import yaml' 2>/dev/null; then
    python3 -c 'import sys,yaml; list(yaml.safe_load_all(open(sys.argv[1])))' "$file" \
      && ok "YAML valid: $file" || die "YAML parse error in $file"
  else
    warn "PyYAML unavailable — skipped YAML syntax check for $file"
  fi
}

wait_for_pods() {  # wait_for_pods LABEL NAME
  local label="$1" name="$2"
  log "Waiting for $name pods ($label) to be ready..."
  if $DRY_RUN; then
    log "  [dry-run] oc wait --for=condition=ready pod -l $label -n $NAMESPACE --timeout=300s"
  else
    if oc wait --for=condition=ready pod -l "$label" -n "$NAMESPACE" --timeout=300s; then
      ok "$name is ready"
    else
      warn "$name did not become ready within 300s — check: oc get pods -l $label -n $NAMESPACE"
      return 1
    fi
  fi
}

# ----------------------------------------------------------------------------
# Step 1 — Verify prerequisites and cluster access
# ----------------------------------------------------------------------------
verify_prereqs() {
  log "=== Step 1: Prerequisites ==="

  # Containerization artifacts (hard requirement)
  [ -f "Containerfile" ]                 || die "Containerfile not found — run mig-containerize first"
  [ -f "openshift/deployment.yaml" ]     || die "openshift/deployment.yaml not found — run mig-containerize first"
  [ -f "openshift/service.yaml" ]        || die "openshift/service.yaml not found — run mig-containerize first"
  for dep in "${!DEP_MANIFEST[@]}"; do
    [ -f "${DEP_MANIFEST[$dep]}" ] || die "Dependency manifest ${DEP_MANIFEST[$dep]} not found"
  done
  ok "Containerization artifacts present (Containerfile + openshift/*.yaml)"

  if $DRY_RUN; then
    warn "Dry-run mode — OpenShift login and cluster state checks are skipped"
    HAS_GRAPH=false
  else
    command -v oc >/dev/null 2>&1 || die "oc CLI not found — install it and run: oc login <cluster-url>"
    oc whoami >/dev/null 2>&1        || die "Not logged into OpenShift — run: oc login <cluster-url>"
    ok "Logged into OpenShift as: $(oc whoami)"
    ok "Cluster: $(oc whoami --show-server)"
  fi

  # rgctl index (optional — enables graph-driven endpoint testing)
  if command -v rgctl >/dev/null 2>&1 && rgctl -f json metrics --pagerank >/dev/null 2>&1; then
    HAS_GRAPH=true;  ok "rgctl index ready — endpoint testing will use the code graph"
  else
    HAS_GRAPH=false; warn "No rgctl index — endpoint testing uses the source-derived fallback list"
  fi
}

# ----------------------------------------------------------------------------
# Step 2 — Namespace
# ----------------------------------------------------------------------------
ensure_namespace() {
  log "=== Step 2: Namespace ==="
  if $DRY_RUN; then
    log "  [dry-run] oc get namespace $NAMESPACE (would create it if missing: $CREATE_NS)"
    return
  fi
  if oc get namespace "$NAMESPACE" >/dev/null 2>&1; then
    ok "Namespace '$NAMESPACE' exists"
  elif $CREATE_NS; then
    oc new-project "$NAMESPACE" >/dev/null
    ok "Created namespace '$NAMESPACE'"
  elif [ -t 0 ]; then
    read -r -p "Namespace '$NAMESPACE' does not exist. Create it? (y/n): " ans
    [ "$ans" = "y" ] || die "Cannot proceed without namespace '$NAMESPACE'"
    oc new-project "$NAMESPACE" >/dev/null
    ok "Created namespace '$NAMESPACE'"
  else
    die "Namespace '$NAMESPACE' does not exist — pass --create-ns (or: oc new-project $NAMESPACE)"
  fi
}

# ----------------------------------------------------------------------------
# Steps 3+4 — Detect dependencies and check what already exists in the cluster
# ----------------------------------------------------------------------------
DETECTED_DEPS=()
MISSING_DEPS=()

detect_dependencies() {
  log "=== Step 3: Application dependencies (from mig-containerize output) ==="
  for dep in postgresql keycloak broker; do
    DETECTED_DEPS+=("$dep")
    log "  - $dep (${DEP_MANIFEST[$dep]}, ${DEP_KIND[$dep]})"
  done
}

check_existing_dependencies() {
  log "=== Step 4: Existing dependencies in cluster ==="
  MISSING_DEPS=()
  if $DRY_RUN; then
    for dep in "${DETECTED_DEPS[@]}"; do
      log "  [dry-run] oc get ${DEP_KIND[$dep]} $dep -n $NAMESPACE (assuming missing → strategy: $DEP_STRATEGY)"
      MISSING_DEPS+=("$dep")
    done
    return
  fi
  local dep found
  for dep in "${DETECTED_DEPS[@]}"; do
    found=false
    oc get "${DEP_KIND[$dep]}" "$dep" -n "$NAMESPACE" >/dev/null 2>&1 && found=true
    oc get deployment "$dep"  -n "$NAMESPACE" >/dev/null 2>&1 && found=true
    oc get statefulset "$dep" -n "$NAMESPACE" >/dev/null 2>&1 && found=true
    if $found; then
      ok "Found existing: $dep"
    else
      MISSING_DEPS+=("$dep")
      warn "Missing: $dep"
    fi
  done
}

# ----------------------------------------------------------------------------
# Step 5 — Handle missing dependencies
# ----------------------------------------------------------------------------
deploy_dependencies() {
  log "=== Step 5: Deploy dependencies (strategy: $DEP_STRATEGY) ==="
  local dep
  if [ "${#MISSING_DEPS[@]}" -eq 0 ]; then
    ok "No missing dependencies — all found in cluster"
    return
  fi

  for dep in "${MISSING_DEPS[@]}"; do
    case "$DEP_STRATEGY" in
      yaml)
        log "Deploying $dep using ${DEP_MANIFEST[$dep]}..."
        apply_yaml "${DEP_MANIFEST[$dep]}"
        if ! wait_for_pods "${DEP_LABEL[$dep]}" "$dep"; then
          die "$dep failed to become ready"
        fi
        ;;
      helm)
        local chart=""
        if [ -n "$HELM_CHARTS" ]; then
          chart=$(printf '%s\n' "$HELM_CHARTS" | tr ',' '\n' | awk -F= -v d="$dep" '$1==d{print $2}')
        fi
        [ -n "$chart" ] || die "No Helm chart configured for $dep — pass --helm-charts $dep=<chart>"
        log "Installing $dep via Helm ($chart)..."
        if $DRY_RUN; then
          log "  [dry-run] helm install $dep $chart -n $NAMESPACE --wait --timeout 5m"
        else
          command -v helm >/dev/null 2>&1 || die "helm CLI not found"
          helm install "$dep" "$chart" --namespace "$NAMESPACE" --wait --timeout 5m
        fi
        ;;
      operator)
        local operator=""
        case "$dep" in
          postgresql) operator="postgresql-operator" ;;
          keycloak)   operator="keycloak-operator" ;;
          broker)     operator="artemis-operator" ;;
          *) die "No operator mapping for $dep — pick another --dep-strategy" ;;
        esac
        if $DRY_RUN; then
          log "  [dry-run] install $operator via Subscription + create $dep CR in $NAMESPACE"
        else
          command -v oc >/dev/null 2>&1 || die "oc CLI required for operator installs"
          if ! oc get csv -n openshift-operators 2>/dev/null | grep -q "$operator"; then
            log "Operator $operator not installed — creating Subscription..."
            oc apply -f - <<EOF
apiVersion: operators.coreos.com/v1alpha1
kind: Subscription
metadata:
  name: $operator
  namespace: openshift-operators
spec:
  channel: stable
  name: $operator
  source: community-operators
  sourceNamespace: openshift-marketplace
EOF
            log "Waiting 30s for operator to settle..."
            sleep 30
          fi
          log "Create the $dep CustomResource for $operator (operator-specific spec)"
          warn "Operator CRs for $dep must be authored per operator docs — no CR applied"
        fi
        ;;
      skip)
        warn "Skipping $dep — assuming an external/managed service."
        warn "  Point coolstore-secrets / coolstore-config at the external endpoints"
        ;;
      *)
        die "Unknown --dep-strategy '$DEP_STRATEGY' (yaml|helm|operator|skip)"
        ;;
    esac
  done

  # Special handling per dependency type (skill Step 5, Option 1)
  if $DRY_RUN; then
    log "  [dry-run] (database) schema migration: handled in-app by Flyway at startup"
    log "  [dry-run] (broker) topics/addresses: orders address auto-created by the app"
  else
    log "Database schema: Flyway runs in-app at startup (baseline-on-migrate, baseline 1.2) — no separate migration Job needed"
    log "Broker addresses: the orders AMQP address is created by the application on connect"
  fi
}

# ----------------------------------------------------------------------------
# Step 6 — Build and push the container image
# ----------------------------------------------------------------------------
detect_registry() {
  if [ -n "$REGISTRY" ]; then return; fi
  if $DRY_RUN; then
    REGISTRY="image-registry.openshift-image-registry.svc:5000"
    log "  [dry-run] registry auto-detect skipped — assuming $REGISTRY"
    return
  fi
  REGISTRY=$(oc get route default-route -n openshift-image-registry -o jsonpath='{.spec.host}' 2>/dev/null || true)
  if [ -z "$REGISTRY" ]; then
    REGISTRY="image-registry.openshift-image-registry.svc:5000"
    warn "No default-route on the internal registry — using in-cluster address $REGISTRY"
    warn "  (expose it with: oc patch configs.imageregistry.operator.openshift.io/cluster"
    warn "     --patch '{\"spec\":{\"defaultRoute\":true}}' --type=merge)"
  fi
}

build_and_push() {
  log "=== Step 6: Build and push container image ==="
  detect_registry
  FULL_IMAGE="$REGISTRY/$NAMESPACE/$APP_NAME:$IMAGE_TAG"
  log "Image: $FULL_IMAGE"

  if $SKIP_BUILD; then
    warn "--skip-build: reusing existing image (make sure it was pushed)"
    return
  fi

  local build_cmd=""
  if command -v podman >/dev/null 2>&1; then build_cmd="podman"
  elif command -v docker >/dev/null 2>&1; then build_cmd="docker"
  elif $DRY_RUN; then build_cmd="podman"
  else die "Neither podman nor docker found — pass --skip-build to reuse an existing image"
  fi

  log "Building image with $build_cmd..."
  if $DRY_RUN; then
    log "  [dry-run] $build_cmd build -t $FULL_IMAGE -f Containerfile ."
    log "  [dry-run] $build_cmd login -u \$(oc whoami) -p \$(oc whoami -t) $REGISTRY"
    log "  [dry-run] $build_cmd push $FULL_IMAGE"
  else
    "$build_cmd" build -t "$FULL_IMAGE" -f Containerfile . \
      || die "Image build failed"
    ok "Image built successfully"
    "$build_cmd" login -u "$(oc whoami)" -p "$(oc whoami -t)" "$REGISTRY" \
      || die "Registry login failed"
    "$build_cmd" push "$FULL_IMAGE" \
      || die "Image push failed"
    ok "Image pushed successfully"
  fi
}

# ----------------------------------------------------------------------------
# Step 7 — Deploy application manifests (safe order)
# ----------------------------------------------------------------------------
deploy_app() {
  log "=== Step 7: Deploy application manifests to namespace: $NAMESPACE ==="

  # 1. Secrets first (required by every workload)
  if [ -n "$SECRET_FILE" ]; then
    [ -f "$SECRET_FILE" ] || die "Secret file not found: $SECRET_FILE"
    apply_yaml "$SECRET_FILE"
    ok "Applied Secret from $SECRET_FILE"
  elif $DRY_RUN; then
    log "  [dry-run] secret check skipped (would require openshift/secret.example.yaml to have real values)"
  else
    if ! oc get secret coolstore-secrets -n "$NAMESPACE" >/dev/null 2>&1; then
      if grep -q "CHANGE_ME" openshift/secret.example.yaml && ! $ALLOW_EXAMPLE_SECRETS; then
        die "coolstore-secrets missing and openshift/secret.example.yaml still contains CHANGE_ME placeholders. Create a real secret (oc create secret generic coolstore-secrets --from-literal=...), pass --secret-file, or use --allow-example-secrets for dev/test"
      fi
      apply_yaml openshift/secret.example.yaml
      local extra=""
      if $ALLOW_EXAMPLE_SECRETS; then extra=" (--allow-example-secrets)"; fi
      warn "Applied the example Secret$extra"
    else
      ok "Secret coolstore-secrets already exists — reusing it"
    fi
  fi

  # 2. Keycloak realm ConfigMap (required by keycloak.yaml)
  if $DRY_RUN; then
    log "  [dry-run] oc create configmap keycloak-realm --from-file=realm-export.json=realm-export.json"
  else
    oc create configmap keycloak-realm --from-file=realm-export.json=realm-export.json \
      -n "$NAMESPACE" --dry-run=client -o yaml | oc apply -f - -n "$NAMESPACE"
    ok "Keycloak realm ConfigMap applied (realm-export.json)"
  fi

  # 3. Application ConfigMap
  apply_yaml openshift/configmap.yaml
  ok "ConfigMap coolstore-config applied"

  # 4. PVCs — none standalone: PostgreSQL storage comes from the StatefulSet
  #    volumeClaimTemplates in openshift/postgresql.yaml (applied in Step 5).

  # 5. ServiceAccount / RBAC — none required (default SA, no cluster perms).

  # 6. Dependencies were deployed in Step 5.

  # 7. Application Deployment (image rewritten to $FULL_IMAGE)
  if $DRY_RUN; then
    apply_yaml openshift/deployment.yaml "s|image: .*|image: $FULL_IMAGE|"
  else
    sed "s|image: .*|image: $FULL_IMAGE|" openshift/deployment.yaml | oc apply -f - -n "$NAMESPACE"
    ok "Deployment $APP_NAME applied (image: $FULL_IMAGE)"
  fi

  # 8. Service
  apply_yaml openshift/service.yaml
  ok "Service $APP_NAME applied"

  # 9. Route (external access)
  apply_yaml openshift/route.yaml
  ok "Route $APP_NAME applied"

  # 10. HPA
  apply_yaml openshift/hpa.yaml
  ok "HPA $APP_NAME applied (2→6 replicas @ 70% CPU)"
}

# ----------------------------------------------------------------------------
# Step 8 — Wait for the deployment to be ready
# ----------------------------------------------------------------------------
wait_rollout() {
  log "=== Step 8: Rollout ==="
  if $DRY_RUN; then
    log "  [dry-run] oc rollout status deployment/$APP_NAME -n $NAMESPACE --timeout=300s"
    return
  fi
  log "Waiting for deployment/$APP_NAME to roll out..."
  if oc rollout status "deployment/$APP_NAME" -n "$NAMESPACE" --timeout=300s; then
    ok "Deployment rolled out successfully"
  else
    warn "Deployment failed to roll out — pod status:"
    oc get pods -n "$NAMESPACE" -l "app=$APP_NAME" || true
    warn "Recent logs:"
    oc logs -n "$NAMESPACE" -l "app=$APP_NAME" --tail=50 || true
    die "Rollout failed (rollback with: oc rollout undo deployment/$APP_NAME -n $NAMESPACE)"
  fi
}

# ----------------------------------------------------------------------------
# Step 9 — Comprehensive health verification
# ----------------------------------------------------------------------------
ROUTE_URL=""

health_pods() {
  log "--- Pod health ---"
  if $DRY_RUN; then
    log "  [dry-run] oc get pods -n $NAMESPACE -l app=$APP_NAME (expect 2/2 Running/Ready)"
    return
  fi
  local pods pod name status ready
  pods=$(oc get pods -n "$NAMESPACE" -l "app=$APP_NAME" -o name)
  [ -n "$pods" ] || die "No pods found for $APP_NAME"
  for pod in $pods; do
    name=${pod#pod/}
    status=$(oc get pod "$name" -n "$NAMESPACE" -o jsonpath='{.status.phase}')
    ready=$(oc get pod "$name" -n "$NAMESPACE" -o jsonpath='{.status.conditions[?(@.type=="Ready")].status}')
    log "  Pod: $name — $status (Ready: $ready)"
    [ "$status" = "Running" ] || die "Pod $name is not Running"
    [ "$ready" = "True" ] || warn "Pod $name is not ready"
  done
  ok "All $APP_NAME pods are running"
}

health_service() {
  log "--- Service health ---"
  if $DRY_RUN; then
    log "  [dry-run] oc get service $APP_NAME -n $NAMESPACE (ClusterIP :80 → 8080)"
    return
  fi
  local ip port
  ip=$(oc get service "$APP_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.clusterIP}')
  port=$(oc get service "$APP_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.ports[0].port}')
  log "  Service: $APP_NAME ($ip:$port)"
  oc run "svc-probe-$RANDOM" --image=curlimages/curl:8.10.1 --restart=Never \
    -n "$NAMESPACE" --rm -i --command -- \
    curl -s -o /dev/null -w "%{http_code}" "http://$ip:$port/q/health/ready" \
    && ok "In-cluster service probe succeeded" \
    || warn "In-cluster service probe failed (check NetworkPolicies)"
}

health_route() {
  log "--- Route health ---"
  if $DRY_RUN; then
    log "  [dry-run] oc get route $APP_NAME -n $NAMESPACE → https://<route-host>/q/health/ready"
    ROUTE_URL="https://<route-host>"
    return
  fi
  local host tls
  host=$(oc get route "$APP_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.host}')
  tls=$(oc get route "$APP_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.tls.termination}')
  [ -n "$host" ] || { warn "No route host found — application not externally accessible"; return; }
  if [ -n "$tls" ]; then ROUTE_URL="https://$host"; else ROUTE_URL="http://$host"; fi
  log "  Route URL: $ROUTE_URL"
  local code
  code=$(curl -s -o /dev/null -w "%{http_code}" -k "$ROUTE_URL/q/health/ready" || echo "000")
  if [ "$code" = "200" ]; then
    ok "Route health endpoint returned 200"
  elif [ "$code" = "000" ]; then
    ok "Route is reachable (health returned $code — may require VPN/network access)"
  else
    warn "Route health endpoint returned HTTP $code"
  fi
}

health_endpoints() {
  log "--- Endpoint tests ---"
  local tests=() line method path expected url code
  if $HAS_GRAPH; then
    log "  Using rgctl graph analysis to derive endpoints..."
    local graph_names=""
    if command -v jq >/dev/null 2>&1; then
      graph_names=$(rgctl -f json gql "MATCH (n:Function) WHERE n.name LIKE '*Endpoint' OR n.name LIKE '*Controller' RETURN n LIMIT 50" 2>/dev/null \
        | jq -r '.rows[]?.node // .rows[]?.name // empty' 2>/dev/null || true)
    fi
    if [ -n "$graph_names" ]; then
      log "  Graph-derived endpoints found: $graph_names"
      warn "  Graph-derived endpoints are heuristic — falling back to the curated list for assertions"
    fi
  else
    log "  Using source-derived endpoint list (no rgctl index)"
  fi
  tests=("${FALLBACK_ENDPOINT_TESTS[@]}")

  if $DRY_RUN; then
    for line in "${tests[@]}"; do
      read -r method path expected <<<"$line"
      log "  [dry-run] curl -X $method $ROUTE_URL$path (expect: $expected)"
    done
    return
  fi
  [ -n "$ROUTE_URL" ] || { warn "No route URL — skipping endpoint tests"; return; }
  local failed=0
  for line in "${tests[@]}"; do
    read -r method path expected <<<"$line"
    url="$ROUTE_URL$path"
    code=$(curl -s -o /dev/null -w "%{http_code}" -k -X "$method" "$url" || echo "000")
    if printf '%s' ",$expected," | grep -q ",$code,"; then
      ok "$method $path → $code (expected $expected)"
    else
      warn "$method $path → $code (expected $expected)"
      failed=1
    fi
  done
  [ "$failed" = "0" ] && ok "All endpoint tests passed" \
                     || warn "Some endpoint tests did not match expectations — review above"
}

health_dependencies() {
  log "--- Dependency connectivity (from the application pod) ---"
  if $DRY_RUN; then
    for dep in "${DETECTED_DEPS[@]}"; do
      log "  [dry-run] oc exec <pod> -- bash /dev/tcp/${DEP_SVC[$dep]} (expect: connected)"
    done
    return
  fi
  local pod dep host port conn
  pod=$(oc get pods -n "$NAMESPACE" -l "app=$APP_NAME" -o name | head -1 | cut -d/ -f2)
  [ -n "$pod" ] || { warn "No application pod for connectivity tests"; return; }
  for dep in "${DETECTED_DEPS[@]}"; do
    host=${DEP_SVC[$dep]%:*}
    port=${DEP_SVC[$dep]#*:}
    conn=$(oc exec "$pod" -n "$NAMESPACE" -- bash -c "timeout 5 bash -c '</dev/tcp/$host/$port' 2>/dev/null && echo connected || echo failed" 2>/dev/null || echo failed)
    if [ "$conn" = "connected" ]; then
      ok "Application can connect to $dep ($host:$port)"
    else
      warn "Application cannot connect to $dep ($host:$port)"
    fi
  done
}

run_smoke_test() {
  log "--- Smoke test Job ---"
  if $SKIP_SMOKE; then
    warn "--skip-smoke: smoke test Job not run"
    return
  fi
  if $DRY_RUN; then
    log "  [dry-run] oc delete job coolstore-smoke-test --ignore-not-found"
    log "  [dry-run] oc apply -f openshift/smoke-test.yaml && oc wait --for=condition=complete job/coolstore-smoke-test"
    return
  fi
  oc delete job coolstore-smoke-test -n "$NAMESPACE" --ignore-not-found
  oc apply -f openshift/smoke-test.yaml -n "$NAMESPACE"
  if oc wait --for=condition=complete job/coolstore-smoke-test -n "$NAMESPACE" --timeout=300s; then
    ok "Smoke test passed:"
    oc logs "job/coolstore-smoke-test" -n "$NAMESPACE" || true
  else
    warn "Smoke test failed — logs:"
    oc logs "job/coolstore-smoke-test" -n "$NAMESPACE" || true
    die "Smoke test failed (see logs above)"
  fi
}

verify_deployment() {
  log "=== Step 9: Health verification ==="
  health_pods
  health_service
  health_route
  health_endpoints
  health_dependencies
  run_smoke_test
}

# ----------------------------------------------------------------------------
# Step 10 — Deployment report
# ----------------------------------------------------------------------------
emit_report() {
  local mode_extra=""
  if $VERIFY_ONLY; then mode_extra=" (verify-only)"; fi
  cat <<EOF

=========================================
  DEPLOYMENT REPORT — CoolStore monolith
=========================================
Mode:        $($DRY_RUN && echo DRY-RUN || echo LIVE)$mode_extra
Timestamp:   $(date -u '+%Y-%m-%dT%H:%M:%SZ')
EOF
  if ! $DRY_RUN; then
    cat <<EOF
Cluster:     $(oc whoami --show-server 2>/dev/null || echo unknown)
Namespace:   $NAMESPACE
Deployed by: $(oc whoami 2>/dev/null || echo unknown)
EOF
  else
    cat <<EOF
Namespace:   $NAMESPACE (target)
Registry:    ${REGISTRY:-<auto>}
Image:       ${FULL_IMAGE:-<not built — dry-run>}
EOF
  fi
  cat <<EOF

--- Application ---
Deployment:  $APP_NAME (Quarkus 3.15.1 / Java 21, fast-jar)
Replicas:    2 (HPA 2→6 @ 70% CPU)
EOF
  if ! $DRY_RUN; then
    cat <<EOF
Ready:       $(oc get deployment "$APP_NAME" -n "$NAMESPACE" -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo '?')/2
Image:       $(oc get deployment "$APP_NAME" -n "$NAMESPACE" -o jsonpath='{.spec.template.spec.containers[0].image}' 2>/dev/null || echo '?')
Route URL:   ${ROUTE_URL:-<none>}
EOF
  fi
  cat <<EOF

--- Dependencies (strategy: $DEP_STRATEGY) ---
EOF
  local dep
  for dep in "${DETECTED_DEPS[@]}"; do
    if $DRY_RUN; then
      log "  $dep: ${DEP_MANIFEST[$dep]} (${DEP_KIND[$dep]})"
    else
      log "  $dep: $(oc get "${DEP_KIND[$dep]}" "$dep" -n "$NAMESPACE" -o jsonpath='{.status.conditions[?(@.type=="Available")].status}' 2>/dev/null || echo 'unknown')"
    fi
  done
  if ! $DRY_RUN; then
    cat <<EOF

--- Pods ---
$(oc get pods -n "$NAMESPACE" -o wide 2>/dev/null || true)

--- Recent events ---
$(oc get events -n "$NAMESPACE" --sort-by='.lastTimestamp' 2>/dev/null | tail -10 || true)
EOF
  fi
  cat <<EOF

=========================================
  ✓ DEPLOYMENT ROUTINE COMPLETE
=========================================
Rollback:    oc rollout undo deployment/$APP_NAME -n $NAMESPACE
Logs:        oc logs -f deployment/$APP_NAME -n $NAMESPACE
Metrics:     oc adm top pods -n $NAMESPACE
EOF
}

# ----------------------------------------------------------------------------
# Rollback
# ----------------------------------------------------------------------------
do_rollback() {
  log "=== Rollback: deployment/$APP_NAME in $NAMESPACE ==="
  if $DRY_RUN; then
    log "  [dry-run] oc rollout undo deployment/$APP_NAME -n $NAMESPACE"
    log "  [dry-run] oc rollout status deployment/$APP_NAME -n $NAMESPACE"
    return
  fi
  oc rollout undo "deployment/$APP_NAME" -n "$NAMESPACE"
  oc rollout status "deployment/$APP_NAME" -n "$NAMESPACE" --timeout=300s
  ok "Rollback complete — verify: oc get pods -n $NAMESPACE && oc logs deployment/$APP_NAME -n $NAMESPACE"
}

# ----------------------------------------------------------------------------
# Argument parsing
# ----------------------------------------------------------------------------
while [ $# -gt 0 ]; do
  case "$1" in
    -n|--namespace)            NAMESPACE="${2:?}"; shift 2 ;;
    --create-ns)               CREATE_NS=true; shift ;;
    --registry)                REGISTRY="${2:?}"; shift 2 ;;
    --image-tag)               IMAGE_TAG="${2:?}"; shift 2 ;;
    --dep-strategy)            DEP_STRATEGY="${2:?}"; shift 2 ;;
    --helm-charts)             HELM_CHARTS="${2:?}"; shift 2 ;;
    --secret-file)             SECRET_FILE="${2:?}"; shift 2 ;;
    --allow-example-secrets)   ALLOW_EXAMPLE_SECRETS=true; shift ;;
    --skip-build)              SKIP_BUILD=true; shift ;;
    --skip-smoke)              SKIP_SMOKE=true; shift ;;
    --verify-only)             VERIFY_ONLY=true; shift ;;
    --rollback)                DO_ROLLBACK=true; shift ;;
    --dry-run)                 DRY_RUN=true; shift ;;
    --report-file)             REPORT_FILE="${2:?}"; shift 2 ;;
    -h|--help)                 usage; exit 0 ;;
    *)                         usage; die "Unknown option: $1" ;;
  esac
done

# ----------------------------------------------------------------------------
# Main
# ----------------------------------------------------------------------------
main() {
  verify_prereqs

  if $DO_ROLLBACK; then
    ensure_namespace
    do_rollback
    exit 0
  fi

  ensure_namespace
  detect_dependencies

  if ! $VERIFY_ONLY; then
    check_existing_dependencies
    deploy_dependencies
    build_and_push
    deploy_app
    wait_rollout
  fi

  verify_deployment

  log "=== Step 10: Deployment report ==="
  if [ -n "$REPORT_FILE" ]; then
    emit_report | tee "$REPORT_FILE"
  else
    emit_report
  fi
}

main "$@"
