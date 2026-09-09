# CLAUDE.md — portfolio-2 backend (Gradle root project name: `portfolio`)

Implementation guidelines for AI agents working in `backend/`. This is the most mature and
actively developed project in the `luiznaac` personal family — treat its conventions as the
reference implementation of the shared Ktor+Spring+Exposed architecture; when
[chameidor](../../chameidor/CLAUDE.md) and this repo disagree on a convention, this one is more
likely to be the up-to-date version.

> This repo is a monorepo: `backend/` (this Gradle project) + `frontend/` (a React SPA that
> consumes the HTTP API — see [../frontend/README.md](../frontend/README.md)). The repo root
> holds the combined `Dockerfile`/`docker-compose.yml`, the `deploy/` templates and a
> script-only `package.json` task runner. **Cross-cutting rule:** `frontend/src/api/types.ts` is
> a hand-maintained mirror of the DTOs the controllers in `http-api/.../controller/` serialize
> (Jackson `SNAKE_CASE`, see `usecase/.../configuration/JsonMapper.kt`). Change a
> request/response shape on one side and update the other in the same commit.

## 1. What this service does

portfolio-2 tracks and consolidates a personal investment portfolio, focused on the Brazilian
fixed-income market:

- **Bonds** (`Bond`/`BondOrder`) — fixed and floating-rate securities, their orders, statements,
  and calculated positions/yield over time.
- **Checking accounts** — balances and movements, consolidated the same way as bonds.
- **Brazilian index rates** (CDI/SELIC) fetched from the Central Bank of Brazil (BACEN) API via
  `BacenGateway`, used to price floating-rate bonds.
- **Tax calculation** — Brazilian IOF (financial transaction tax, front-loaded and decaying with
  holding time) and Renda (income tax, bracket depends on holding time), via
  `IOFIncidenceCalculator`/`RendaIncidenceCalculator`.
- **Reporting** — generates XLSX/PDF statements and uploads them.
- **Scheduling** — registers its own periodic consolidation/reporting jobs with
  **[chameidor](../../chameidor/CLAUDE.md)** (a separate service in this same family) via
  `ChameidorGateway`, instead of running its own scheduler.

## 2. Architecture

Same layered multi-module shape and wiring model as chameidor — read
[chameidor/CLAUDE.md §2](../../chameidor/CLAUDE.md) first if you haven't; it is not repeated in full
here. Summary:

```
application  →  http-api  →  usecase  ←  persistence
                  gateway  →  usecase
```

- **`usecase`** — all domain logic and models, organized **by business feature, not by technical
  layer** (see §3). Depends on nothing but Kotlin/`kotlinx-*`/Spring DI annotations.
- **`persistence`** — Exposed-backed implementations of the `usecase` repository interfaces
  (bond, checking account, index), plus `TransactionService`.
- **`gateway`** — outbound integrations: `BacenGateway` (BACEN index rates API),
  `ChameidorGateway` (registers scheduled jobs with chameidor), plus health checks for both.
- **`http-api`** — Ktor routes as `@Component` classes implementing `ControllerTemplate`
  (`BondController`, `BondOrderController`, `CheckingAccountController`,
  `ConsolidationController`, `IndexController`, `IndexValueController`, `UploadController`,
  `HealthController`), plus `PdfConverter`/`XlsxConverter` for report generation.
- **`application`** — composition root (`Boot.kt`), `application.yaml`.
- **`integrationTest`** — full-stack tests via docker-compose.

## 3. Design principles

- **Package by feature, not by layer.** Inside `usecase`, every business concept — `bond/`,
  `checkingaccount/`, `index/`, `tax/`, `upload/`, `consolidation/`, `schedule/` — is its own
  package, and each one has its own `model/`, `repository/`, and/or `gateway/` sub-packages as
  needed. **When adding a new domain concept, create a new top-level package under `usecase/`
  following this shape rather than adding to `commons/` or spreading files across existing
  packages.** `commons/` is for genuinely cross-cutting helpers only (see
  `CommonExtensions.kt`/`CommonDefinitions.kt`).
- **Interfaces live in `usecase`, implementations live where the technology is.**
  `IBondRepository`/`ICheckingAccountRepository`/`IIndexRepository`/`IIndexValueRepository` are in
  `usecase`, implemented against Exposed in `persistence`. `IScheduleGateway` is in `usecase`,
  implemented by `ChameidorGateway` in `gateway`. Follow this for any new port.
  `ITransactionTemplate` (in `usecase/configuration`) abstracts "run this in a DB transaction" so
  usecase-level services can compose multi-repository operations without importing Exposed.
- **Complex calculations get their own class, separate from orchestration.** e.g.
  `BondCalculator` (pure calculation) is distinct from `BondConsolidationService` (orchestrates
  fetching data + calling the calculator + persisting results) and `BondConsolidator`/
  `BondContributionConsolidator` (aggregation logic). Keep this separation for new
  calculation-heavy features — it keeps the math independently unit-testable from the wiring.
- **Money and rates are `BigDecimal`, always.** Never use `Double`/`Float` for anything
  bond/tax/currency-related. Be deliberate about scale/rounding — see existing tests
  (`BondCalculatorTest`, `IOFIncidenceCalculatorTest`, `RendaIncidenceCalculatorTest`) for the
  expected precision handling before adding new calculations.
- **Model "creation" separately from the persisted entity** when the two shapes differ (e.g.
  `BondCreation` vs. `Bond`, `IndexValueCreation` vs. `IndexValue`,
  `CheckingAccountMovementCreation`) — this mirrors chameidor's `TaskCreation` pattern.
- Domain-specific "context" and "result" model objects (`BondCalculationContext`/
  `BondCalculationResult`, `BondConsolidationContext`, `CheckingAccountConsolidationContext`) are
  used to pass structured intermediate state through multi-step calculations instead of long
  parameter lists — follow this shape for new multi-step domain logic.

## 4. How to implement a new feature (walkthrough)

Example: adding a new consolidated product type (portfolio-2 already has `ProductType` in
`consolidation/` as an extension point for this).

1. **Create a feature package** under `usecase/src/main/kotlin/dev/agner/portfolio/usecase/<feature>/`
   with `model/` for domain types and `repository/`/`gateway/` for ports, following the `bond/` or
   `checkingaccount/` package as a template.
2. **Define repository/gateway interfaces** (`I<Name>Repository`) in the feature package.
3. **Write the service(s)**. If the feature involves non-trivial math, split a pure calculator
   (like `BondCalculator`) from an orchestrating service (like `BondConsolidationService`).
4. **Register with `ProductConsolidator`/`ConsolidationService`** in `consolidation/` if the new
   feature should participate in overall portfolio consolidation, following how bonds/checking
   accounts are wired in there.
5. **Implement persistence** in `persistence/src/main/kotlin/dev/agner/portfolio/persistence/<feature>/`
   with an Exposed `Table`, `Entity`, and `@Component class <Name>Repository : I<Name>Repository`.
   If the feature needs a new table or column, write the migration for it too — see §7.
6. **Expose HTTP endpoints** in `http-api/.../controller/` as a `@Component class
   <Name>Controller(...) : ControllerTemplate` — no manual registration needed, same as chameidor.
7. **Add tests**: unit tests for the calculator/service in `usecase`'s `src/test/kotlin`
   (use/extend `testFixtures` helpers — `BasicHelpers.kt`, `BondConsolidationHelpers.kt`,
   `TaxHelpers.kt` — instead of duplicating test data builders), and an `http-api` test if you add
   response conversion logic (see `XlsxConverterTest`).

## 5. Code style

Same Detekt setup as chameidor: `config/detekt/{config,format}.yml`, `maxIssues: 0`,
`autoCorrect: true`, `MaximumLineLength: 120`, trailing commas mandatory, no wildcard imports,
`allWarningsAsErrors = true`. Run `./gradlew detekt` before finishing a change.

## 6. Testing

Kotest `StringSpec`, MockK, `kotest-extensions-spring`. Reuse `testFixtures` builders rather than
hand-rolling fixture objects per test. Existing unit test coverage worth using as a style
reference: `BondCalculatorTest`, `BondConsolidationServiceTest`,
`BondContributionConsolidatorTest`, `YieldRateServiceTest`, `CheckingAccountConsolidatorTest`,
`IndexValueServiceTest`, `TaxServiceTest`, `IOFIncidenceCalculatorTest`,
`RendaIncidenceCalculatorTest`.

```bash
./gradlew test                 # unit tests
./gradlew testCoverageReport   # aggregated JaCoCo report
```

## 7. Database migrations

The schema is versioned SQL under `persistence/src/main/resources/db/migration/V*.sql` — there is
no more `mysql/init.sql`. Two tools, each doing one half of the job:

- **Exposed's migration module** (`persistence/.../migration/MigrationScripts.kt`) *generates* the
  SQL by diffing `allTables` (every `Table` object, defined in the same file) against a live
  database. It never applies anything.
- **Flyway** (`persistence/.../migration/Migrator.kt`) *applies* those `V*.sql` files. It runs as
  a standalone `main()` — packaged as a second start script, `bin/migrate`, alongside
  `bin/application` (see `application/build.gradle.kts`) — invoked from `deploy/entrypoint.sh`
  before the app starts. Not from the Spring context: `KtorConfig` blocks the main thread for the
  process's entire lifetime (`ktor.wait: true`), so nothing hooked into Spring's lifecycle would
  run before the server starts accepting requests anyway. A failed migration aborts the container
  instead of serving traffic against a stale schema. `baselineOnMigrate` means a database that
  already has some of the tables gets stamped at V1 rather than having it re-applied — **V1
  deliberately only covers the 7 tables production had when migrations were introduced** (`index`,
  `index_value`, `checking_account`, `bond`, `bond_order`, `bond_order_statement`,
  `bond_order_position`); everything added since (`listed_asset` and its family in V2, the Fase 1
  allocation-engine tables in V3) is a real migration that Flyway actually runs against
  production, not something baselining could silently skip. Keep this shape for future baseline
  changes: **V1 (or whichever version you baseline at) must never describe more than what
  production already has** — anything extra in it would never actually get created there.
  V4 widens two columns that were already wider in the Exposed `Table` objects than what
  production physically has — that ALTER runs for real on production (V1 is invisible to it) and
  is a no-op on any database that went through V1 directly.
- One residual, harmless divergence from baselining: production's original 7 tables keep their
  physical `TIMESTAMP DEFAULT CURRENT_TIMESTAMP` columns and MySQL's auto-generated FK constraint
  names (`bond_ibfk_1`, …) forever, since V1's own `CREATE TABLE` statements never run against
  them — only freshly-created databases get Exposed's `DATETIME(6)` / `fk_..._id` naming. Harmless
  because every repository sets `createdAt` explicitly (see `TaskRepository`-style patterns) and
  because a constraint's name has no behavioral effect — but don't be surprised if you inspect
  production's schema and see it doesn't byte-for-byte match what `MigrationSchemaTest` (below)
  guards.

Changing a table:

1. Edit the `Table` object in `persistence/.../<feature>/` (or add it to `allTables` if it's new).
2. Point `MYSQL_HOST`/`MYSQL_USER`/`MYSQL_PASSWORD` at a database already migrated to head, then
   `./gradlew :persistence:generateMigrationScript -Pname=V5__add_something` (or
   `npm run db:generate -- -Pname=V5__add_something` from the repo root). Review the generated
   `.sql` before committing — the diff is mechanical and won't know a rename is a rename rather
   than a drop-and-add.
3. `./gradlew :persistence:migrate` (or `npm run db:migrate`) to apply it locally.

`integrationTest/.../tests/MigrationSchemaTest.kt` is the guard: `DockerComposeExtension`
migrates the compose-provided MySQL to head before any spec runs, and this test asserts
`MigrationUtils.statementsRequiredForDatabaseMigration(*allTables)` is empty. If a `Table`
changes without a matching migration (or vice versa), this test fails.

## 8. Configuration

`application/src/main/resources/application.yaml`:

| Key | Source | Notes |
|---|---|---|
| `ktor.port` / `ktor.wait` | fixed | `8080` / `true` |
| `mysql.host` / `mysql.user` / `mysql.password` | `MYSQL_HOST` / `MYSQL_USER` / `MYSQL_PASSWORD` | required |
| `gateways.bacen.host` | fixed | `https://api.bcb.gov.br` |
| `gateways.chameidor.host` | fixed (overridable) | `http://localhost:8081` locally — chameidor must be running separately for scheduling to work end-to-end |
| `app-own-host` | fixed | `localhost:8080` — the host portfolio-2 tells chameidor to call back when registering a scheduled job |

## 9. Build, run, deploy

```bash
./gradlew clean build
./gradlew test
./gradlew testCoverageReport
```

Local dev: `docker compose -f backend/docker-compose.yml up -d mysql` from the repo root (or
`npm run db`) — MySQL 9.4.0 only, database `portfolio`, seeded from `backend/mysql/init.sql` —
then run via the IntelliJ config in `backend/.run/` with `MYSQL_HOST=localhost`,
`MYSQL_USER=root`, `MYSQL_PASSWORD=`. If you need scheduled-job registration to actually fire,
also run chameidor locally on port `8081`.

Docker: the repo-root `Dockerfile` is a multi-stage build (frontend SPA → `gradle:8.14-jdk21`
backend distribution → `eclipse-temurin:21-jre-jammy` runtime) that ships backend + built SPA in
one image: `supervisord` runs the JVM app (`API_PORT`/8080) and `nginx` (`deploy/nginx.conf.template`
— serves the SPA on `WEB_PORT`/8081, reverse-proxies `/api` → the app). The repo-root
`docker-compose.yml` adds MySQL for full-stack runs; `backend/docker-compose.yml` is the
MySQL-only file used by `integrationTest` (via Testcontainers) and by plain backend dev.

## 10. Git & CI

- Remote: `git@github.com:luiznaac/portfolio-2.git`.
- **Dependabot is active** — expect `dependabot/gradle/...` branches with auto-merged PRs; don't
  manually bump a dependency Dependabot already has a PR open for.
- Commits: short, imperative (`"consolidate in transaction"`, `"Fixed rate calculation"`).
  Feature branches are named after the domain concept (`bond-full-redemption`,
  `checking-account`, `yield-service`).

**AI agents: never commit directly to `master`.** Always create a feature branch and open a PR,
even for a small or "obviously safe" change — no exceptions for agent-authored commits.

## 11. Related repositories

Uses [chameidor](../../chameidor/CLAUDE.md) as its scheduling backend and was generated from the same
[environments/kotlin](../../environments/CLAUDE.md) template. If you introduce a new cross-cutting
convention here that should also apply to chameidor or the template, call it out explicitly rather
than letting the three silently drift apart.
