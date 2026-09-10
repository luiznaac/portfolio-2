# DEVELOPMENT.md — portfolio-2 monorepo

Development guidelines for anyone (human, agent, or tool) working in this repository.

Two projects, one repo:

- **`backend/`** — the Ktor + Spring + Exposed service (Gradle multi-module, root project name
  `portfolio`). All backend commands run from `backend/` (`cd backend && ./gradlew <task>`).
  Architecture, conventions and rules for evolving it are in
  [backend/DEVELOPMENT.md](backend/DEVELOPMENT.md) — read that before touching `backend/`.
- **`frontend/`** — the React 19 + Vite + TypeScript + Tailwind v4 SPA. Commands run from
  `frontend/` (`npm --prefix frontend run <script>`). Details in
  [frontend/README.md](frontend/README.md).

## Cross-cutting rules

### The API contract is mirrored by hand

`frontend/src/api/types.ts` is a hand-maintained mirror of the request/response DTOs that the
controllers in `backend/http-api/.../controller/` serialize — Jackson is configured
`SNAKE_CASE`, non-null, ISO dates in `backend/usecase/.../configuration/JsonMapper.kt`. Any
change to a DTO on one side must update the other in the **same commit**. The backend has no
generated schema; this mirror is the contract.

### The code is in English

Identifiers, enum constants, comments, log lines, exception messages, test names, commit messages
and branch names are English throughout both projects — including Brazilian financial jargon, which
gets its English name plus a gloss where the translation isn't obvious. Two exceptions:

- **External data** stays verbatim: B3 column headers, regexes matching Portuguese PDFs, broker
  labels. Those strings have to match something outside this repo.
- **User-facing copy in `frontend/`** is pt-BR, because its user is. Enum keys crossing the API are
  English; their pt-BR labels live in `frontend/src/i18n/`, never in the enum itself.

See [backend/DEVELOPMENT.md](backend/DEVELOPMENT.md) for the backend specifics, including what to do
when renaming an enum whose values are persisted as strings.

## Git workflow

**Do not commit directly to `master`.** Always create a feature branch and open a PR,
even for a small or "obviously safe" change. This applies to all contributors.

## Tooling

Root `package.json` holds script shims only (`npm run be:check`, `npm run fe:build`,
`npm run check`, `npm run db`, `npm run db:migrate`, `npm run db:generate -- -Pname=V5__x`,
`npm run up`). It has no dependencies and is not a real package.
`.pre-commit-config.yaml` lives at the root and scopes hooks by path (`^backend/`, `^frontend/`).

## Docker

One image (repo-root `Dockerfile`, multi-stage) ships backend + frontend together: `supervisord`
runs the JVM app (`API_PORT`/8080) and `nginx` (`deploy/nginx.conf.template` — serves the built
SPA on `WEB_PORT`/8081 and reverse-proxies `/api` → the app). No DB in the image. Repo-root
`docker-compose.yml` adds MySQL for full-stack / DB-only local runs; `backend/docker-compose.yml`
is the MySQL-only file consumed by the `integrationTest` module via Testcontainers — keep the two
MySQL definitions in sync. The schema comes from
`backend/persistence/src/main/resources/db/migration/V*.sql`, applied by Flyway (`bin/migrate` in
the image) from `deploy/entrypoint.sh` before the app starts — see
[backend/DEVELOPMENT.md](backend/DEVELOPMENT.md) §7.

`.github/workflows/ci.yml` runs `backend` (Gradle `clean build`) and `frontend`
(`npm ci && typecheck && build`) jobs on every push to master and every PR. Its `publish` job
(`needs: [backend, frontend]`, push-to-master only) then builds the repo-root `Dockerfile` and
pushes `luiznaac/portfolio` with tags `latest` and `v<run-number>` (a sequential build number,
`github.run_number`) — so the image is published only after a green CI run.
