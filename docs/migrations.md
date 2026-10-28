# Migration baseline history

Read this before changing what an early migration version (`V1`, or whichever version is the current baseline) describes, or before diagnosing a schema mismatch against production. For the day-to-day "how do I add a migration" steps, see `backend/AGENTS.md` and salgadinhos' `kotlin-db-migration` skill — this file is just the "why" behind the baseline.

## Why V1 only has 7 tables

Flyway's `baselineOnMigrate` stamps a database that already has tables — but no `flyway_schema_history` row — at the baseline version instead of re-running it. **V1 deliberately only covers the 7 tables production had when migrations were introduced**: `index`, `index_value`, `checking_account`, `bond`, `bond_order`, `bond_order_statement`, `bond_order_position`.

The rule this establishes for any future baseline change: **the baseline version must never describe more than what the database being baselined already has** — anything extra in it would never actually get created there, since baselining skips running it.

Everything added since is a real migration Flyway actually executes against production, not something baselining could silently skip:

- **V2** — `listed_asset` and its family.
- **V3** — the Fase 1 allocation-engine tables.
- **V4** — widens two columns that were already wider in the Exposed `Table` objects than what production physically had. This one *does* run for real on production (V1 predates it, so it's invisible to the baseline) and is a no-op on any database that went through V1 directly (since those columns started at the wider width there).

## A harmless, permanent divergence

Production's original 7 tables keep their physical `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` columns and MySQL's auto-generated FK constraint names (`bond_ibfk_1`, …) forever — V1's own `CREATE TABLE` statements never actually run against production, only against a freshly-created database, which gets Exposed's `DATETIME(6)` / `fk_..._id` naming instead.

This is harmless: every repository sets `createdAt` explicitly (see `TaskRepository`-style patterns, so the `TIMESTAMP` default is never relied on), and a constraint's name has no behavioral effect. But don't be surprised if you inspect production's schema and see it doesn't byte-for-byte match what `integrationTest/.../MigrationSchemaTest.kt` guards against a freshly migrated database.
