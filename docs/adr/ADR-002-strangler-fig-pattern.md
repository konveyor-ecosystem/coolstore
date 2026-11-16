# ADR-002: Adopt the Strangler Fig Pattern for Incremental Migration

**Status**: Accepted  
**Date**: 2026-11-16  
**Deciders**: Platform Engineering, Architecture Review Board  
**Supersedes**: N/A  

---

## Context

The CoolStore monolith is a single WAR deployed to JBoss EAP / WebLogic. A full "big-bang" rewrite carries significant risk:
- High likelihood of regression across all functional areas simultaneously.
- Long delivery freeze (months) with no incremental value delivery.
- Difficult rollback if the new system is not ready.

The team considered three migration strategies:

| Strategy                 | Risk   | Delivery cadence | Rollback ease |
|--------------------------|--------|------------------|---------------|
| Big-bang rewrite         | High   | Low (one release)| Hard          |
| Lift-and-shift (repackage)| Medium | Medium           | Medium        |
| Strangler Fig (incremental)| Low  | High (per slice) | Easy          |

---

## Decision

**We will use the Strangler Fig Pattern to migrate CoolStore slice-by-slice behind a routing proxy.**

The approach:
1. Stand up the new Quarkus service alongside (not replacing) the monolith.
2. Place an **API Gateway / OpenShift Route** in front of both.
3. Route individual API paths (`/api/catalog`, `/api/cart`, `/api/orders`, `/api/inventory`) to the new Quarkus service as each is migrated and validated.
4. The monolith continues to serve un-migrated paths until all slices are complete.
5. Once all routes are migrated and stable (minimum 2-week soak per slice), decommission the monolith.

Migration order (driven by coupling analysis):
1. **Catalog** – read-mostly, no MDB dependency → first candidate.
2. **Inventory** – depends on catalog; `InventoryNotificationMDB` replaced by SmallRye AMQP.
3. **Cart** – stateful, depends on catalog + inventory.
4. **Orders** – depends on all above; `OrderServiceMDB` replaced last.

---

## Consequences

**Positive**
- Each slice is independently releasable and testable in production traffic.
- Rollback of any slice = flip the route back to the monolith (seconds, not hours).
- Enables parallel team streams: one team on monolith maintenance, another on Quarkus slices.
- Real production traffic validates each migrated slice before proceeding.

**Negative / Risks**
- Dual-write / dual-read scenarios during transition: the same database is accessed by both monolith (Hibernate 5 / JPA 2) and Quarkus (Hibernate ORM 6 / JPA 3).
- Schema changes must be backward-compatible throughout the migration period.
- Increased operational overhead (two running applications, two deployment pipelines) during transition.
- API Gateway becomes a critical path dependency; must be highly available.

**Mitigation**
- Flyway is used by the Quarkus service for schema migrations; a `baseline-version` strategy ensures the monolith's existing schema is not re-run.
- Schema changes during transition are strictly additive (no drops, no renames).
- OpenShift Routes with weight-based traffic splitting are used for gradual rollout (canary).
- Both pipelines share the same Tekton infrastructure (see ADR-003).
