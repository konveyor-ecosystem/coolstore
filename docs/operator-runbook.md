# CoolStore Quarkus Operator Runbook

## Overview

This runbook covers day-2 operations for the CoolStore Quarkus application deployed on OpenShift.

## Prerequisites

- `oc` CLI authenticated to the OpenShift cluster
- Access to `coolstore-dev`, `coolstore-staging`, `coolstore-prod` namespaces

## Common Operations

### Check Application Status

```bash
oc get pods -n coolstore-dev -l app=coolstore-quarkus
oc get deployment coolstore-quarkus -n coolstore-dev
oc rollout status deployment/coolstore-quarkus -n coolstore-dev
```

### View Application Logs

```bash
oc logs -l app=coolstore-quarkus -n coolstore-dev --tail=100 -f
```

### Check Health Endpoints

```bash
# Readiness
oc exec deploy/coolstore-quarkus -n coolstore-dev -- curl -s http://localhost:8080/q/health/ready
# Liveness
oc exec deploy/coolstore-quarkus -n coolstore-dev -- curl -s http://localhost:8080/q/health/live
```

### Scale Application

```bash
# Manual scale (HPA will override if CPU/memory thresholds trigger)
oc scale deployment/coolstore-quarkus --replicas=4 -n coolstore-dev
```

### Restart Application

```bash
oc rollout restart deployment/coolstore-quarkus -n coolstore-dev
oc rollout status deployment/coolstore-quarkus -n coolstore-dev
```

### Rollback Deployment

```bash
# Via ArgoCD (recommended)
# Revert the Git commit that changed the image tag, then ArgoCD syncs automatically.

# Via oc (manual emergency rollback)
oc rollout undo deployment/coolstore-quarkus -n coolstore-dev
```

### View Audit Logs

```bash
# Access audit log PVC
oc exec deploy/coolstore-quarkus -n coolstore-dev -- \
  ls /deployments/device-inventory-audit-logs/
oc exec deploy/coolstore-quarkus -n coolstore-dev -- \
  tail -50 /deployments/device-inventory-audit-logs/audit.log
```

### Rotate Dead-Letter Queue Messages

```bash
# Port-forward to AMQ management console
oc port-forward svc/coolstore-amq-broker-console-jolokia-0-svc 8161:8161 -n coolstore-dev
# Open browser: http://localhost:8161/console
# Navigate to: Addresses > DLQ.topic.orders > Messages > Browse/Retry
```

## Disaster Recovery

### Database Backup (CrunchyData PGO)

```bash
oc get postgrescluster coolstore-db -n coolstore-dev
oc annotate postgrescluster coolstore-db \
  -n coolstore-dev \
  postgres-operator.crunchydata.com/pgbackrest-backup=""
```

### Secret Rotation

```bash
# Rotate DB password
oc create secret generic coolstore-quarkus-secret \
  --from-literal=DB_PASSWORD=NEW_PASSWORD \
  -n coolstore-dev --dry-run=client -o yaml | oc apply -f -

# Trigger rolling restart to pick up new secret
oc rollout restart deployment/coolstore-quarkus -n coolstore-dev
```
