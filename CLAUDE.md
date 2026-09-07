# CLAUDE.md — portfolio-2 monorepo

Two projects, one repo:

- **`backend/`** — the Ktor + Spring + Exposed service (Gradle multi-module, root project name
  `portfolio`). All backend commands run from `backend/` (`cd backend && ./gradlew <task>`). Its
  architecture, conventions and the rules for evolving it are in
  [backend/CLAUDE.md](backend/CLAUDE.md) — read that before touching `backend/`.
- **`frontend/`** — the React 19 + Vite + TypeScript + Tailwind v4 SPA. Commands run from
  `frontend/` (`npm --prefix frontend run <script>`). Details in
  [frontend/README.md](frontend/README.md).

## The one cross-cutting rule

`frontend/src/api/types.ts` is a hand-maintained mirror of the request/response DTOs that the
controllers in `backend/http-api/.../controller/` serialize — Jackson is configured
`SNAKE_CASE`, non-null, ISO dates in `backend/usecase/.../configuration/JsonMapper.kt`. Any
change to a DTO on one side must update the other in the **same commit**. The backend has no
generated schema; this mirror is the contract.

## Tooling

Root `package.json` holds script shims only (`npm run be:check`, `npm run fe:build`,
`npm run check`, `npm run db`, `npm run up`). It has no dependencies and is not a real package.
`.pre-commit-config.yaml` lives at the root and scopes hooks by path (`^backend/`, `^frontend/`).

## Docker

One image (repo-root `Dockerfile`, multi-stage) ships backend + frontend together: `supervisord`
runs the JVM app (`API_PORT`/8080) and `nginx` (`deploy/nginx.conf.template` — serves the built
SPA on `WEB_PORT`/8081 and reverse-proxies `/api` → the app). No DB in the image. Repo-root
`docker-compose.yml` adds MySQL for full-stack / DB-only local runs; `backend/docker-compose.yml`
is the MySQL-only file consumed by the `integrationTest` module via Testcontainers — keep the two
MySQL definitions in sync.

`.github/workflows/test.yml` runs `backend` (Gradle `clean build`) and `frontend`
(`npm ci && typecheck && build`) jobs; `docker-image.yml` publishes `luiznaac/portfolio:latest`
after a green master build.
