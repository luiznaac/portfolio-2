# Code map

Hand-authored module → key files → seam index, plus the app entry points — read it to go from a named seam to the file a change belongs to without a `find`/`ls` sweep. The vocabulary is the `codebase-design` one (**module**, **interface**, **seam**, **adapter**). The backend is a Gradle multi-module build (`backend/settings.gradle.kts`): `application → http-api → usecase ← persistence` and `gateway → usecase`; each Gradle module is one block below, then the frontend SPA and the repo-root infra.

## Modules

### `backend/usecase` — domain logic and models
**Purpose**: every business concept plus the interfaces the other layers implement. Organized by feature, not by layer — each concept is its own package with its own `model/`, `repository/` and/or `gateway/`. Depends on Kotlin/`kotlinx-*` (coroutines, datetime), Jackson, Logback and Spring DI annotations — never on `persistence`, `gateway` or `http-api`.
**Files**: `bond/` (`BondService.kt`, `BondOrderService.kt`, `statement/BondStatementService.kt`, `position/BondPositionService.kt`, `consolidation/{BondConsolidationService,BondCalculator,BondConsolidator,BondContributionConsolidator,YieldRateService}.kt`, `model/{Bond,BondOrder,BondOrderStatement,BondCreation}.kt`, `repository/I{Bond,BondOrder,BondOrderStatement}Repository.kt`, `position/repository/IBondOrderPositionRepository.kt`); `checkingaccount/` (`CheckingAccountService.kt`, `CheckingAccountConsolidator.kt`, `position/CheckingAccountPosition.kt`, `model/*`, `repository/ICheckingAccountRepository.kt`); `index/` (`IndexService.kt`, `IndexValueService.kt`, `gateway/IIndexValueGateway.kt`, `model/{Index,IndexId,IndexValue,IndexValueCreation,TheirIndexValue}.kt`, `repository/I{Index,IndexValue}Repository.kt`); `tax/` (`TaxService.kt`, `incidence/{TaxIncidenceCalculator,IOFIncidenceCalculator,RendaIncidenceCalculator}.kt`, `incidence/model/TaxIncidence.kt`); `catalog/` (`CatalogService.kt`, `gateway/ICotahistSource.kt`, `model/{AssetClass,AssetKind,CatalogEntry,CotahistRow,TickerNormalization}.kt`, `repository/ICatalogRepository.kt`); `quotes/` (`QuoteService.kt`, `model/Quote.kt`, `repository/IQuoteRepository.kt`); `listedasset/` (`TradeService.kt`, `DuplicateTradeException.kt`, `model/{Trade,TradeCreation,TradeKey,TradeSource}.kt`, `repository/ITradeRepository.kt`); `institution/` (`InstitutionService.kt`, `model/{Institution,InstitutionCreation,InstitutionNameNormalization}.kt`, `repository/IInstitutionRepository.kt`); `import/` (`ImportService.kt`, `InvalidImportStateException.kt`, `model/{Import,ImportCreation,ImportRow,ImportRowPayload,FileHash}.kt`, `repository/IImportRepository.kt`); `upload/` (`UploadService.kt`, `model/UploadOrder.kt`); `consolidation/` (`ConsolidationService.kt`, `ProductConsolidator.kt`, `ProductType.kt`); `schedule/` (`ScheduleService.kt`, `gateway/IScheduleGateway.kt`, `model/ScheduleContext.kt`); `health/` (`HealthChecker.kt`, `HealthGateway.kt`, `HealthCheckResult.kt`); `commons/` (`DomainException.kt`, `CommonExtensions.kt`, `CommonDefinitions.kt`); `configuration/` (`ITransactionTemplate.kt`, `JsonMapper.kt`, `TimeProviderConfig.kt`, `serializers/*`)
**Seam(s)**:
- **At the `@Service` classes** — these are what the controllers call; a class's public `suspend` methods are its interface (`TradeService.createManual/reconcile/fetchAll`, `ImportService.stage/review/apply`, `BondConsolidationService.consolidate`). Tests cross the same seam (e.g. `TradeServiceTest`, `ImportServiceTest`, `BondConsolidationServiceTest`).
- **At the `I*Repository`/`I*Gateway` interfaces** — implemented by `persistence`/`gateway`; a new integration is a new interface here plus an adapter there.
- **At `consolidation/ProductConsolidator.kt`** — the extension point (`type`/`getConsolidatableIds`/`buildContext`/`consolidate`) plus `ProductType.kt`; a new consolidatable product implements it and is auto-collected by `ConsolidationService`.
- **At `configuration/ITransactionTemplate.kt`** — implemented by `persistence`'s `TransactionService`, it lets a usecase service compose multi-repository writes without importing Exposed.
- **At `configuration/JsonMapper.kt`** — the Jackson `SNAKE_CASE` shape the DTOs cross to `frontend/src/api/types.ts`; change one side and the other in the same commit.

### `backend/persistence` — Exposed adapters and migrations
**Purpose**: implements the usecase repository interfaces against MySQL via Exposed, and owns the Flyway migration tooling and the transaction template.
**Files**: per feature, a `*Repository.kt` adapter beside its `*Table.kt` — `bond/{Bond,BondOrder,BondOrderStatement,BondOrderPosition}{Repository,Table}.kt`, `checkingaccount/`, `catalog/`, `quotes/`, `index/{Index,IndexValue}*`, `institution/`, `listedasset/Trade*`, `import/{Import,ImportRow}*`; `configuration/{DatabaseConfig,TransactionService}.kt`; `health/MySqlConnectionHealthCheck.kt`; `migration/{MigrationScripts,Migrator,MysqlEnv}.kt`; `src/main/resources/db/migration/V*.sql`
**Seam(s)**:
- **Adapter seam** — each `@Component … : I*Repository` sits at the usecase interface; `TransactionService : ITransactionTemplate` at the transaction seam. The `Table` object is where the persisted shape lives; a model with a variant (e.g. `Trade.Buy`/`Trade.Sell`) translates from its single stored column in the repository (`TradeTable.toModel`, pinned by `TradeModelMappingTest`).
- **Migration seam** — a new `*Table.kt` must be added to `allTables` in `migration/MigrationScripts.kt`; schema change = edit the `Table`, generate the SQL (`:persistence:generateMigrationScript -Pname=V<n>__x`), apply it (`:persistence:migrate`, Flyway). `MigrationSchemaTest` diffs `allTables` against a migrated database and fails on drift. Baseline rules: `docs/migrations.md`.
- **Connection seam** — `configuration/DatabaseConfig.mysqlJdbcUrl` is shared by Exposed and Flyway, so the two cannot drift.

### `backend/gateway` — outbound integrations
**Purpose**: adapters for the external systems the domain calls: BACEN index rates, B3 COTAHIST, chameidor scheduling, and health.
**Files**: `index/BacenGateway.kt`; `cotahist/{CotahistGateway,CotahistParser}.kt`; `schedule/ChameidorGateway.kt`; `health/{AppHealthGateway,HttpClientHealthCheck}.kt`; `configuration/KtorClientConfig.kt`
**Seam(s)**: each `@Service`/`@Component … : I*Gateway` sits at its usecase interface (`IIndexValueGateway`, `ICotahistSource`, `IScheduleGateway`, `HealthGateway`); endpoint hosts and the chameidor token arrive through `@Value("\${gateways.*}")`. `KtorClientConfig` is the shared `HttpClient` seam. `ChameidorGatewayContractTest` pins the chameidor request body against `contracts/chameidor/tasks-one-time-request.json`.

### `backend/http-api` — Ktor routes and edge adapters
**Purpose**: the HTTP surface — controllers, broker-file parsers, report converters, domain-exception handling and the Ktor server bootstrap.
**Files**: `controller/` — `ControllerTemplate.kt`, `ControllerExtensions.kt`, and `{Bond,BondOrder,CheckingAccount,Consolidation,Catalog,Index,IndexValue,Institution,Import,Trade,Upload,Health}Controller.kt`; `import/{IImportParser,ImportParserResolver}.kt`; `configuration/{KtorConfig,DomainExceptionHandling,DomainExceptionStatusMapper,PdfConverter,XlsxConverter}.kt`
**Seam(s)**:
- **Route seam** — `controller/ControllerTemplate.kt` (`routes(): RouteDefinition`); `KtorConfig` collects every `@Component` implementing it and mounts it. Adding an endpoint is adding a controller — no manual registration. Route path-param parsing goes through `ControllerExtensions.kt` (`requiredInt`/`requiredString` → a `400` domain error).
- **Parser seam** — `import/IImportParser.kt` is the broker-layout interface (`shouldExecute`/`parse`); `ImportParserResolver` injects the `List<IImportParser>` and picks the first that claims the file. A new layout is a new class implementing the interface. The domain never sees bytes — `ImportController.stageFile` hashes the upload and hands the parsed rows to `ImportService.stage`.
- **Error-body seam** — `configuration/DomainExceptionHandling.kt` maps a `DomainException` to the canonical body; `contracts/error-response.json` pins it, asserted by `DomainExceptionContractTest`.
- **Content-negotiation seam** — `configuration/{XlsxConverter,PdfConverter}.kt` register the report formats on the Ktor `ContentNegotiation`.

### `backend/application` — composition root
**Purpose**: the Spring Boot entry and the runtime configuration; wires the modules together.
**Files**: `Boot.kt`, `src/main/resources/{application.yaml,application-prod.yaml,logback.xml}`, `build.gradle.kts`
**Seam(s)**: `Boot.kt`'s `@ComponentScan("dev.agner.portfolio")` is the wiring seam — every `@Component`/`@Service` is picked up, so a new class needs no registration. `application.yaml` is the config seam (env-driven `mysql.*` and `gateways.chameidor.token`; the full list is in `backend/AGENTS.md` §Configuration). `build.gradle.kts` adds the second start script `bin/migrate` (main class `MigratorKt`) beside `bin/application`.

### `backend/integrationTest` — full-stack tests
**Purpose**: end-to-end specs that boot the real app against a real MySQL and stubbed external gateways.
**Files**: `config/{DockerComposeExtension,IntegrationTest,HttpMockService,HttpRequestService,ClockMock,KotestConfig,ResetWiremock,StopKtorServer}.kt`; `helpers/*`; `tests/{Bond,CheckingAccount,Consolidation,MigrationSchema}Test.kt`
**Seam(s)**: `DockerComposeExtension` brings up the compose MySQL and migrates it to head before any spec runs; `HttpMockService` (WireMock) stubs the outbound gateways. `MigrationSchemaTest` is the schema guard (see `persistence`). Run via the `integrationTest` Gradle task.

### `frontend` — React SPA
**Purpose**: the browser client — the scaffold's health-check slice plus the first product slice (shell sidebar, B3 import wizard, Ativos trade list); the rest is still scaffold to be rewritten later.
**Files**: `src/main.tsx`, `src/App.tsx`; `src/api/{client,queries,types}.ts`; `src/pages/{Dashboard,ImportWizard,Trades}.tsx`; `src/components/{Layout,Sidebar,Panel,SituationChip,ImportStatusChip}.tsx`; `src/lib/{format,importSituations,tradeFilters}.ts`
**Seam(s)**:
- **Wire contract** — `src/api/types.ts` is the hand-maintained mirror of the backend edge DTOs; any DTO change updates it in the same commit.
- **Data seam** — `src/api/client.ts` (the `fetch` wrapper, `ApiError` and the multipart `upload`) and `src/api/queries.ts` (TanStack Query hooks) are where a new API call is wired; `pages/Dashboard.tsx` is the reference read slice and `pages/ImportWizard.tsx` the reference mutation slice.
- **Route seam** — `App.tsx` holds the route table (`/` health, `/imports`, `/trades`); `main.tsx` mounts the router and QueryClient. Pure helpers in `lib/` are the unit-tested surface (`format.test.ts`, `importSituations.test.ts`, `tradeFilters.test.ts`); there are no component-render tests yet.

### Repo root — contracts, deploy, CI
**Purpose**: the cross-cutting fixtures and the packaging/deploy story that bind backend + frontend together.
**Files**: `contracts/error-response.json`, `contracts/chameidor/tasks-one-time-request.json`; `Dockerfile`, `docker-compose.yml`, `deploy/{entrypoint.sh,supervisord.conf,nginx.conf.template}`; `package.json` (script shims only), `.github/workflows/ci.yml`, `.pre-commit-config.yaml`; `docs/{migrations.md,acceptance-runbook.md,adr/*}`
**Seam(s)**: `contracts/*` are the versioned wire fixtures the tests pin against. The one image runs the JVM app (`API_PORT`/8080) and nginx (`WEB_PORT`/8081, reverse-proxying `/api`) under supervisord; `deploy/entrypoint.sh` runs `bin/migrate` then the app. `package.json` is a script-only shim (`npm run check`, `npm run db:migrate`, `npm run up`, …), not a real package.

## Entry points

- `backend/application/src/main/kotlin/dev/agner/portfolio/application/Boot.kt` — the app: Spring Boot `main()` that component-scans `dev.agner.portfolio` and starts the server.
- `backend/http-api/src/main/kotlin/dev/agner/portfolio/httpapi/configuration/KtorConfig.kt` — the HTTP route table: mounts every `ControllerTemplate`, installs content negotiation, CORS and the domain-exception handler, and blocks the main thread (`ktor.wait: true`).
- `backend/persistence/src/main/kotlin/dev/agner/portfolio/persistence/migration/Migrator.kt` — `main()`, the Flyway migrator packaged as `bin/migrate`; `deploy/entrypoint.sh` runs it before the app.
- `backend/persistence/src/main/kotlin/dev/agner/portfolio/persistence/migration/MigrationScripts.kt` — `main()`, the Exposed migration-script generator.
- `frontend/src/main.tsx` — the SPA: mounts React with the TanStack Query client and the router.
- `deploy/entrypoint.sh` + `deploy/supervisord.conf` — the container entry: migrate, then run the JVM app and nginx.
