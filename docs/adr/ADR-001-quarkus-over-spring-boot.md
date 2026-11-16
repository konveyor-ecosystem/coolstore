# ADR-001: Choose Quarkus 3.x Over Spring Boot for Monolith Migration

**Status**: Accepted  
**Date**: 2026-11-16  
**Deciders**: Platform Engineering, Architecture Review Board  
**Supersedes**: N/A  

---

## Context

The CoolStore application is currently a Java EE 7 monolith running on JBoss EAP / WebLogic. It uses:
- JAX-RS, CDI, JPA, JMS/MDBs, Servlet
- Proprietary WebLogic JNDI (`weblogic.jndi.WLInitialContextFactory`)
- Keycloak adapter via `keycloak.json`

The team must select a modern cloud-native Java framework to migrate to. The primary candidates evaluated were:

| Criterion                     | Quarkus 3.x           | Spring Boot 3.x        |
|-------------------------------|-----------------------|------------------------|
| Native image (GraalVM)        | First-class support   | Experimental (AOT)     |
| Jakarta EE API compatibility  | Native (Jakarta 10)   | Partial (via adapters) |
| CDI standard                  | Yes (ArC subset)      | No (Spring IoC)        |
| Reactive messaging (MDB repl) | SmallRye / MicroProfile | Spring Cloud Stream  |
| Red Hat support               | Fully supported (RHBQ)| Community only on OCP  |
| Dev Services                  | Built-in              | TestContainers only    |
| Memory footprint (JVM)        | ~150 MB RSS           | ~250 MB RSS            |
| Startup time (JVM)            | ~0.5 s                | ~2–4 s                 |

The organization already operates OpenShift (Red Hat) and has existing investment in Red Hat middleware.

---

## Decision

**We will migrate CoolStore to Quarkus 3.6.4 (backed by Red Hat Build of Quarkus).**

Key reasons:
1. **Jakarta EE alignment** – Quarkus natively uses Jakarta EE 10 APIs, minimising code changes from the `javax.*` → `jakarta.*` namespace shift.
2. **MicroProfile** – Quarkus implements MicroProfile 6, providing drop-in replacements for JAX-RS (`resteasy-reactive`), CDI, Health, Metrics, and JWT/OIDC.
3. **SmallRye Reactive Messaging** – directly replaces JMS MDBs (`InventoryNotificationMDB`, `OrderServiceMDB`) with an AMQP channel model, eliminating WebLogic JNDI dependencies.
4. **Red Hat support contract** – Red Hat Build of Quarkus (RHBQ) is fully supported on OpenShift, aligning with existing vendor relationships.
5. **Operational efficiency** – Sub-second startup and lower memory footprint reduce resource costs on the shared cluster.

---

## Consequences

**Positive**
- Elimination of proprietary WebLogic/JBoss EAP APIs and JNDI lookups.
- Stateless, cloud-native deployment model (12-factor compliant).
- Smaller container images; potential for native executable in a future story.
- Unified observability stack via MicroProfile Health + Micrometer/Prometheus.

**Negative / Risks**
- Developers must learn Quarkus extension model and ArC CDI restrictions (no dynamic proxies for all cases).
- SmallRye ArC CDI is a subset of full CDI; some advanced CDI features (e.g., `@Dependent` with certain interceptors) need adjustment.
- RESTEasy Reactive is non-blocking by default; blocking operations must be explicitly annotated with `@Blocking`.

**Mitigation**
- Developer enablement workshops planned before Story 2 (service layer migration).
- Lint rules added to flag `javax.*` imports and WebLogic-specific classes during CI.
