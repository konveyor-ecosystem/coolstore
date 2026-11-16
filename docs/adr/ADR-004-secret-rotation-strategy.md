# ADR-004: Secret Rotation Strategy

**Status**: Accepted  
**Date**: 2026-11-16  
**Deciders**: Platform Engineering, Security Team, Architecture Review Board  
**Supersedes**: N/A  

---

## Context

The CoolStore monolith contained several hardcoded or statically configured secrets:

| Secret / Pattern | Location | Severity |
|---|---|---|
| `weblogic.jndi.WLInitialContextFactory` JNDI factory class | `InventoryNotificationMDB.java:21` | Medium – proprietary class removed in migration |
| JMS broker credentials (username/password in `jndi.properties`) | Monolith config | High |
| Keycloak client secret (was in `keycloak.json` adapter config) | WEB-INF (not present in this repo copy) | High |
| Database credentials in `standalone.xml` / `datasource.xml` | EAP server config | Critical |
| Default session encryption key | Application code | High |

Hardcoded secrets violate:
- **NIST SP 800-53 IA-5** (Authenticator Management)
- **CIS Benchmark for Kubernetes** (Section 5.4 – Secrets Management)
- Internal security policy SEC-012 (Secret Lifecycle Management)

Options evaluated for secret management in the Quarkus + OpenShift target state:

| Option | Description | Pros | Cons |
|---|---|---|---|
| A – OpenShift Secrets (base64) | Native k8s Secrets | Simple, no extra dependency | Secrets in etcd; base64 is not encryption |
| B – Vault by HashiCorp (chosen as primary) | External secret store with dynamic credentials | Rotation without redeployment; audit trail; dynamic DB creds | Operational overhead; additional infra |
| C – OpenShift Secrets Encryption + ESO | External Secrets Operator syncing from Vault/AWS SM | GitOps-friendly; declarative | Requires ESO installation |
| D – Sealed Secrets | Bitnami Sealed Secrets (encrypted at rest in Git) | GitOps-safe | Static; no dynamic rotation |

---

## Decision

**We will use a layered secret management strategy:**

1. **HashiCorp Vault** (or Red Hat Secrets Manager) as the authoritative secret store for all production credentials.
2. **External Secrets Operator (ESO)** to sync secrets from Vault into namespace-scoped OpenShift Secrets on a TTL (every 1h for static creds; dynamic DB creds renewed by Vault agent).
3. **Quarkus environment variable injection** – all secrets are consumed via `${ENV_VAR}` placeholders in `application.properties`. No secret value ever appears in source code or container images.
4. **Sealed Secrets** for non-sensitive configuration that must be stored in Git (e.g., non-secret ConfigMaps committed to the GitOps repo).

### Specific secret handling

| Secret | Strategy |
|---|---|
| `DB_PASSWORD` | Vault dynamic credential (PostgreSQL secrets engine); 1h TTL, auto-renewed by Vault Agent sidecar |
| `OIDC_CLIENT_SECRET` | Vault KV v2; rotated quarterly; ESO syncs to `coolstore-oidc-secret` OpenShift Secret |
| `AMQP_PASSWORD` | Vault KV v2; rotated monthly; ESO syncs to `coolstore-amqp-secret` OpenShift Secret |
| `SESSION_ENCRYPTION_KEY` | Vault KV v2; 256-bit random key; rotated on each major release |
| CI/CD image registry push secret | Short-lived OIDC token (Tekton + Quay robot account with token expiry 24h) |

### Eliminated secrets
- `weblogic.jndi.WLInitialContextFactory` – class reference removed; replaced by SmallRye AMQP connector configuration (no JNDI).
- Keycloak JSON adapter (`keycloak.json`) – replaced by Quarkus OIDC extension; client secret injected via `OIDC_CLIENT_SECRET` env var.
- Hardcoded JMS credentials in `jndi.properties` – eliminated; AMQP credentials injected via `AMQP_USERNAME` / `AMQP_PASSWORD` env vars.

### Developer workflow
- Local development: `.env` file (git-ignored) or Quarkus Dev Services (no credentials needed for H2/Kafka Dev Services).
- CI pipeline: Vault AppRole auth; short-lived token fetched at pipeline start; secrets never logged.
- Rotation runbook: `docs/runbooks/secret-rotation.md` (to be created in Story 4).

---

## Consequences

**Positive**
- Zero hardcoded credentials in source code, container images, or Git history.
- Dynamic database credentials eliminate the risk of long-lived credential compromise.
- Full audit trail in Vault for every secret access event (supports SOC 2 / PCI-DSS compliance).
- Automated rotation removes manual toil and reduces risk of "forgotten" static secrets.
- Dev profile uses H2 + disabled OIDC → zero secrets needed for local development.

**Negative / Risks**
- Vault is an additional operational dependency; a Vault outage can prevent new pods from starting (secrets unavailable).
- Vault Agent sidecar adds ~50 MB to pod memory footprint.
- Initial setup complexity: AppRole configuration, ESO CRD installation, Vault policy authoring.
- Developer onboarding requires understanding of the secret injection model (no more editing config files directly).

**Mitigation**
- Vault deployed in HA mode (3 replicas) on dedicated infra nodes; DR snapshot every 6h to S3.
- Quarkus pods cache the last-known secret in a Kubernetes Secret (ESO-managed); if Vault is briefly unavailable, existing pods continue running with cached values.
- Secret rotation runbook and "day-2 operations" guide delivered as part of Story 4.
- Developer quick-start guide documents local dev setup (`.env` template committed to repo; actual values in `.gitignore`).
