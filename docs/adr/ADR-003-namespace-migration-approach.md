# ADR-003: Namespace Migration Approach on OpenShift

**Status**: Accepted  
**Date**: 2026-11-16  
**Deciders**: Platform Engineering, OpenShift Cluster Admins, Architecture Review Board  
**Supersedes**: N/A  

---

## Context

The CoolStore monolith is currently deployed in a single OpenShift project (namespace) `coolstore-legacy`. The migration to Quarkus introduces a need to:

1. Stand up new Quarkus workloads without disrupting the monolith in production.
2. Manage environment promotion (dev → staging → prod) cleanly.
3. Control RBAC, NetworkPolicy, and ResourceQuota boundaries per environment.
4. Avoid namespace sprawl while maintaining clear separation of concerns.

Options evaluated:

| Option | Description | Risk |
|---|---|---|
| A – Single namespace | Monolith and Quarkus in `coolstore-legacy` | High coupling; cannot isolate failures |
| B – Per-service namespaces | One namespace per migrated slice | Namespace sprawl; complex NetworkPolicy |
| C – Environment-based namespaces (chosen) | `coolstore-dev`, `coolstore-staging`, `coolstore-prod` | Balanced isolation and manageability |

---

## Decision

**We will use environment-based namespaces: `coolstore-dev`, `coolstore-staging`, `coolstore-prod`.**

Namespace layout:

```
coolstore-dev        ← active development; both monolith (read-only) and Quarkus slices
coolstore-staging    ← integration testing; mirrors prod topology
coolstore-prod       ← production; monolith runs until fully strangled
coolstore-cicd       ← Tekton pipelines, image build tasks, shared tooling
```

Key policies per namespace:
- **ResourceQuota**: dev (4 CPU / 8 Gi), staging (8 CPU / 16 Gi), prod (16 CPU / 32 Gi + HPA).
- **NetworkPolicy**: inter-namespace traffic from `coolstore-dev` to `coolstore-prod` database is **denied**; each environment has its own PostgreSQL instance.
- **RBAC**: developers have `edit` in `coolstore-dev`; `view` in staging; no direct access to prod (pipeline-only deploys via service account).
- **ImageStream**: images built in `coolstore-cicd` are promoted (tagged, not rebuilt) to staging and prod.

Tekton pipeline service account `pipeline` in `coolstore-cicd` is granted `system:image-pusher` and `edit` in target namespaces via RoleBinding.

---

## Consequences

**Positive**
- Clear blast radius: a broken deployment in `coolstore-dev` cannot affect `coolstore-prod`.
- Promotion model (image tagging) ensures the same artifact tested in staging reaches prod.
- NetworkPolicy provides defence-in-depth; secrets are namespace-scoped.
- OpenShift quotas prevent runaway resource consumption in dev.

**Negative / Risks**
- Cross-namespace service calls (e.g., Quarkus in `coolstore-dev` calling monolith endpoints) require explicit NetworkPolicy `allow` rules.
- Namespace provisioning must be automated (OpenShift-GitOps / ArgoCD) to avoid configuration drift.
- Shared `coolstore-cicd` namespace is a potential single point of failure for all pipelines.

**Mitigation**
- ArgoCD ApplicationSet manages namespace provisioning from Git (`deploy/namespaces/`).
- `coolstore-cicd` runs on dedicated infra nodes with PodDisruptionBudget.
- Cross-namespace connectivity is documented in the network topology runbook (`docs/runbooks/network-topology.md`).
