# Local development runbook — portfolio-2

The backend + SPA loop the [README](../../README.md) Quickstart does not carry. The app does not read `.env` yet, so the required settings arrive from the environment or from the IntelliJ run config in `backend/.run/`.

## Boot the stack

```bash
npm run setup       # install the SPA's dependencies
npm run db          # MySQL on :8112, database portfolio
npm run db:migrate  # apply the schema
```

Export the required settings before starting the backend (the dev fixtures are in [`.env.example`](../../.env.example)):

```bash
export MYSQL_HOST=localhost
export MYSQL_USER=root
export MYSQL_PASSWORD=
export CHAMEIDOR_TOKEN=dev-portfolio-fixture-token
```

Then run the two processes in separate shells:

```bash
npm run be:run      # API on :8110
npm run fe:dev      # SPA on :8113, proxying /api to :8110
```

Open `http://localhost:8113/`. The Vite dev server uses `/` as its base; the combined image serves the SPA under `/portfolio/`.

## Scheduled jobs

`CHAMEIDOR_TOKEN` is required at boot even when chameidor is not running. For scheduled consolidation and reporting to fire, start chameidor on its API role (`8100`) and register the same token there; the backend's default target is `http://localhost:8100`.

## Integration lane

`npm run be:integration` starts its own Testcontainers MySQL, so stop the dev MySQL first or accept the extra container. It needs Docker 25+.
