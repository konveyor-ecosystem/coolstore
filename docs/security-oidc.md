# Security — OpenID Connect (Keycloak)

How authentication works in the CoolStore monolith, and how to operate it.

## OIDC flow

```
browser (AngularJS UI + keycloak.js)
   │  1. login redirect (public client eap-app, realm eap)
   ▼
Keycloak realm `eap` (openshift/keycloak.yaml)
   │  2. OIDC authorization-code flow → access token (JWT)
   ▼
browser ── Authorization: Bearer <JWT> ──▶ /services REST API (Quarkus)
                                             3. quarkus-oidc validates:
                                                signature (JWKS), issuer,
                                                expiry → 200 / 401
```

1. The front end initializes keycloak.js from `keycloak.json`
   (realm `eap`, public client `eap-app`).
2. "Sign in" redirects to the Keycloak `eap` realm; the public client needs
   no client secret (browser-only client, `public-client: true`).
3. keycloak.js attaches `Authorization: Bearer <JWT>` to `/services` calls.
4. `quarkus-oidc` validates the bearer token server-side against the issuer
   configured in `QUARKUS_OIDC_AUTH_SERVER_URL` (in-cluster:
   `http://keycloak:8080/realms/eap`).

## Realm import

- `realm-export.json` (~59 KB, repo root) defines realm `eap` with the public
  client `eap-app`.
- On OpenShift it is mounted into the Keycloak pod from the `keycloak-realm`
  ConfigMap at `/opt/keycloak/data/import/realm-export.json` and imported at
  boot (`start --import-realm`) — see the header of `openshift/keycloak.yaml`
  for the `oc create configmap keycloak-realm …` prerequisite.
- For local dev, import the realm manually via the Keycloak console (see
  README, "Local backing services").

## Permission model

| Endpoint group | Access |
|---|---|
| `GET /services/products` (and inventory/catalog browsing) | public — anonymous |
| `/services/cart`, `/services/orders` (checkout, order submission) | authenticated user of realm `eap` |

Anonymous users can browse the catalog; cart and checkout require a valid
bearer token — the same behaviour as the legacy EAP deployment.

## keycloak.json per environment

`keycloak.json` (`auth-server-url`) is consumed **by the browser**, so it must
point at the *externally reachable* Keycloak of each environment:

| Environment | `auth-server-url` (browser) | `QUARKUS_OIDC_AUTH_SERVER_URL` (backend) |
|---|---|---|
| local dev | `http://localhost:8081/` | `http://localhost:8081/realms/eap` |
| OpenShift | `https://keycloak-<namespace>.<cluster-domain>` (Route) | `http://keycloak:8080/realms/eap` (Service) |

Because the value differs per environment, do not hard-code it into the
image. Recommended handling: serve `keycloak.json` per environment from a
ConfigMap instead of the packaged static file:

```bash
oc create configmap coolstore-keycloak-js \
  --from-file=keycloak.json=keycloak.openshift.json
# mount it over the webapp static resource (or substitute the URL in CI)
```

Keep the browser-facing URL and the Route consistent — see the issuer note
below.

## Token troubleshooting (why 401?)

| Cause | Symptom / check | Fix |
|---|---|---|
| Issuer mismatch | token `iss` carries the Route URL but the backend validates the Service URL (or vice versa) | point `QUARKUS_OIDC_AUTH_SERVER_URL` at the same issuer the browser token carries, or set `quarkus.oidc.token.issuer=any` |
| Expired token | 401 after ~5 min idle (realm `accessTokenLifespan: 300`) | keycloak.js refreshes automatically; re-login if the tab was closed |
| Wrong realm / typo | issuer is not `…/realms/eap` | fix `auth-server-url` / `QUARKUS_OIDC_AUTH_SERVER_URL` |
| Clock skew | freshly issued token rejected | sync node clocks (NTP) |
| JWKS unreachable | app logs show network errors fetching `…/realms/eap/protocol/openid-connect/certs` | check the Keycloak Service, DNS and network policies |
| Header missing | requests arrive without `Authorization` | keycloak.js not initialized — check the browser console for a `keycloak.json` 404 |

Decode the token to inspect `iss`/`exp` (e.g. jwt.io) and check
`oc logs deployment/coolstore-quarkus` for quarkus-oidc diagnostics.

## Test profile

OIDC is **disabled under `%test`**: `mvn verify` runs the whole suite without
a Keycloak — tests exercise the REST layer directly, on H2 with the JMS
consumers disabled. Tests that need an authenticated context stub the security
identity rather than calling a real identity provider.
