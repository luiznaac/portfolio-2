# AGENTS.md — portfolio-2 monorepo

Development guidelines for anyone (human, agent, or tool) working in this repository.

Two projects, one repo:

- **`backend/`** — the Ktor + Spring + Exposed service (Gradle multi-module, root project name
  `portfolio`). All backend commands run from `backend/` (`cd backend && ./gradlew <task>`).
  Architecture, conventions and rules for evolving it are in
  [backend/AGENTS.md](backend/AGENTS.md) — read that before touching `backend/`.
- **`frontend/`** — the React 19 + Vite + TypeScript + Tailwind v4 SPA. Commands run from
  `frontend/` (`npm --prefix frontend run <script>`). Details in
  [frontend/README.md](frontend/README.md). It is currently reset to the `environments/react`
  scaffold (health-check slice only) and will be rewritten from scratch in future plans — the
  former hand-mirrored `src/api/types.ts` DTO contract no longer exists.

## Tooling

Root `package.json` holds script shims only (`npm run be:check`, `npm run fe:build`,
`npm run check`, `npm run db`, `npm run db:migrate`, `npm run db:generate -- -Pname=V5__x`,
`npm run up`). It has no dependencies and is not a real package.
`.pre-commit-config.yaml` lives at the root and scopes hooks by path (`^backend/`, `^frontend/`),
and carries `no-commit-to-branch` — the git/PR conventions are enforced there, not merely stated
(see `salgadinhos/global/AGENTS.md`).

## Docker

One image (repo-root `Dockerfile`, multi-stage) ships backend + frontend together: `supervisord`
runs the JVM app (`API_PORT`/8080) and `nginx` (`deploy/nginx.conf.template` — serves the built
SPA on `WEB_PORT`/8081 and reverse-proxies `/api` → the app). No DB in the image. Repo-root
`docker-compose.yml` adds MySQL for full-stack / DB-only local runs; `backend/docker-compose.yml`
is the MySQL-only file consumed by the `integrationTest` module via Testcontainers — keep the two
MySQL definitions in sync. The schema comes from
`backend/persistence/src/main/resources/db/migration/V*.sql`, applied by Flyway (`bin/migrate` in
the image) from `deploy/entrypoint.sh` before the app starts — see
[backend/AGENTS.md](backend/AGENTS.md).

`.github/workflows/ci.yml` runs `backend` (Gradle `clean build`) and `frontend`
(`npm ci && typecheck && build`) jobs on every push to master and every PR. Its `publish` job
(`needs: [backend, frontend]`, push-to-master only) then builds the repo-root `Dockerfile` and
pushes `luiznaac/portfolio` with tags `latest` and `v<run-number>` (a sequential build number,
`github.run_number`) — so the image is published only after a green CI run.
