# Persistence Runbook — PostgreSQL / Hibernate ORM / Flyway

Database concerns for the CoolStore Quarkus monolith: schema management,
compatibility with the legacy database, backup/restore and rollback notes.

## Stack

| Layer | Technology | Configuration |
|---|---|---|
| Datasource | PostgreSQL | `QUARKUS_DATASOURCE_URL` / `_USERNAME` / `_PASSWORD` (env-injected) |
| ORM | Hibernate ORM (`quarkus-hibernate-orm`) | `quarkus.hibernate-orm.*` |
| Schema migration | Flyway (`quarkus-flyway`) | `quarkus.flyway.*`, scripts in `src/main/resources/db/migration` |

## Migrations (unchanged from legacy)

| Script | Content |
|---|---|
| `db/migration/V1_1__CreateSchema.sql` | Creates `INVENTORY`, `ORDERS`, `ORDER_ITEMS`, `PRODUCT_CATALOG` |
| `db/migration/V1_2__AddInitialData.sql` | Seeds inventory and product-catalog rows |

Both scripts are byte-for-byte identical to the ones the legacy EAP
application ran — no SQL was rewritten during the migration.

`quarkus.flyway.migrate-at-start=true`: the schema is created/verified during
application startup, before the readiness probe turns green, so traffic only
reaches pods whose schema is migrated. The startup probe (`/q/health/started`,
up to 150 s) tolerates the extra boot time of a cold migration.

## Why baseline-on-migrate / baseline-version=1

The legacy application ran **Flyway 4.1.2 programmatically**: the
`DataBaseMigrationStartup` `@Singleton` called `baseline()` followed by
`migrate()` against an already-initialized schema, and its history lived in
the Flyway 4 table `schema_version`. Flyway 10 (used by `quarkus-flyway`)
reads `flyway_schema_history` instead. Therefore, when the Quarkus application
is first pointed at an **existing** legacy database:

- without baselining, Flyway aborts with
  *"found non-empty schema without schema history table"* and startup fails;
- with `quarkus.flyway.baseline-on-migrate=true` and
  `quarkus.flyway.baseline-version=1`, Flyway replicates the legacy
  behaviour: it stamps baseline version 1 on the existing schema, then applies
  the pending migrations `V1_1` (1.1) and `V1_2` (1.2) on top.

On **fresh/empty** databases — such as the PostgreSQL deployed from
`openshift/postgresql.yaml` — the full schema and seed data are created by
`V1_1`/`V1_2` on first boot; no manual step is required.

> **Caution** — if a database contains the tables *and* a legacy
> `schema_version` table recording 1.1/1.2 as already applied, reconcile
> manually before the first Quarkus deploy: back up, then baseline the
> history table at 1.2 (`flyway baseline -baselineVersion=1.2`) or drop the
> legacy table and let Flyway re-baseline. This avoids re-running `V1_1`
> (plain `CREATE TABLE`, no `IF NOT EXISTS`) against existing objects.

## Zero data migration

Schema and seed data are identical between legacy and Quarkus deployments:
there is **no data-conversion step**. The Quarkus application reads and writes
the same tables the EAP WAR used, so both can run side by side during a
migration window (coordinate order `orderId` sequences when running both
writers simultaneously).

## Backup and restore

```bash
# Backup (custom-format dump):
oc exec deployment/postgresql -- pg_dump -U coolstore -Fc coolstore \
  > coolstore-$(date +%F).dump

# Inspect a dump:
pg_restore --list coolstore-2026-11-06.dump | head

# Restore (drop and recreate objects, then reload):
oc exec -i deployment/postgresql -- \
  pg_restore -U coolstore -d coolstore --clean --if-exists \
  < coolstore-2026-11-06.dump
```

Take a backup before any schema-affecting change. Every commit that adds a
`V*__*.sql` script under `src/main/resources/db/migration` changes the schema
once it reaches `main` (CI applies it on deploy) — treat those PRs
accordingly.

## Rollback note

The schema is backwards-compatible with the legacy application: the **legacy
EAP WAR remains deployable** against the same PostgreSQL database at any
time. If a bad migration ever lands, the recovery path is: restore the latest
dump (above), then redeploy the previous image tag — or, worst case, the
legacy WAR. Full procedure in [operations-runbook.md](operations-runbook.md).
