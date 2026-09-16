---
source: luiznaac/portfolio-2#119
branch: research/ticker-catalog
date: 2026-09-16
---

# Ticker catalog source for B3 (stocks, FIIs, ETFs, BDRs) — findings and recommendation

Research for [issue #119](https://github.com/luiznaac/portfolio-2/issues/119), 2026-09-16.

**Question:** which free source seeds the B3 ticker catalog automatically — ticker, type
(STOCK/FII/ETF/BDR), name — and at what cadence, so the Importar B3 slice has a registry before any
manual creation?

Context: the quote-API research (#95) already chose **B3 COTAHIST** as the EOD price backbone and
**brapi.dev** for current quotes. This ticket decides the *catalog*: the registry the trade-import
wizard checks tickers against, with create-on-import only as a fallback. All live evidence below
was probed on 2026-09-16 from this machine.

## TL;DR recommendation

**Derive the catalog from the COTAHIST daily file — the same nightly job #95 already chose for
prices — and keep `GET https://brapi.dev/api/available` as a free cross-check.** No new
third-party dependency: every COTAHIST record already carries ticker, a short name, two type/segment
codes (BDI + TPMERC) and the ISIN. Sync it daily in the same run as the quote job, upsert with
`last_seen` and never delete (delisted tickers remain for historical positions), and classify
STOCK / FII / ETF / BDR from the BDI code. brapi's endpoint (verified: HTTP 200, no token) returns a
comparable ticker universe but with **no names and no types**, so it is a diff/fallback only — not
the catalog source.

## Evidence

### 1. COTAHIST daily file (parsed: `COTAHIST_D15092026.ZIP`, 2026-09-15 session)

- **17,101 records**, of which the à-vista universes:
  - `TPMERC 010` (mercado à vista): **1,444 records**
  - `TPMERC 020` (fracionário): **382 records**
  - the remaining ~15,000 are option markets (`TPMERC 070`: 7,707; `TPMERC 080`: 7,441) — a naive
    full-file ingest must filter them out.
- **BDI distribution within `TPMERC 010`** (the type discriminator):
  `34`: 431 · `02`: 318 · `12`: 286 · `14`: 243 · `36`: 121 · `08`: 16 · `35`: 9 · `22`: 9 ·
  `07`: 7 · `13`: 2 · `10`: 2.
- **Verbatim samples** (ticker | BDI | TPMERC | NOMRES):
  - `PETR4 | 02 | 010 | PETROBRAS` (stock)
  - `SANB11 | 02 | 010 | SANTANDER BR`, `TAEE11 | 02 | 010 | TAESA` — **units classify as stocks
    by BDI, not by the `-11` suffix**, so suffix heuristics are unreliable
  - `MXRF11 | 12 | 010 | FII MAXI REN`, `HGLG11 | 12 | 010 | FII HGLG PAX` (FIIs)
  - `BOVA11 | 14 | 010 | ISHARES BOVA`, `IVVB11 | 14 | 010 | ISHARE SP500` (ETFs/funds)
  - `AAPL34 | 34 | 010 | APPLE` (BDR)
  - `ENJU3F | 96 | 020 | ENJOEI` (fractional market)
- **Record layout:** 245-char fixed width; the 12-char short name is truncated
  (`FII MAXI REN` for "FII MAXI RENDA"); ISIN is present — PETR4's record contains
  `BRPETRACNPR6` at 0-based offset 230.
- **Freshness:** per #95, the daily file is published after close (observed ~20:37 BRT, not a
  contractual time) and the current-year annual file is refreshed daily — the catalog rides the
  same download the price sync already does.

### 2. brapi.dev `/api/available`

- Live probe: `GET https://brapi.dev/api/available` → **HTTP 200 without a token**;
  body `{"stocks":[...], "indexes":["^BVSP","IFIX.SA"]}` — **1,867 tickers** (369 fractional
  `…F`, 606 `…11` names).
- **Bare ticker strings only** — no name, no type, no market segment. Useful as a ticker-universe
  cross-check (e.g. catching a ticker that has not traded yet and therefore is absent from
  COTAHIST), not as the catalog itself.

### 3. B3 official instruments/listed-companies registries

- The public "empresas listadas" / "lista de instrumentos" pages are JS apps backed by
  undocumented proxies; guessed proxy paths (`listedCompaniesProxy/CompanyCall/GetInitialCompanies`,
  `arquivos.b3.com.br/apinegocios/ticker`) returned **404** in this time-box, and the page HTML
  carries no downloadable CSV/XLSX link. No stable, free, documented registry found that adds
  fields COTAHIST lacks.

## Recommendation in detail

1. **Source:** COTAHIST-derived, reusing the #95 nightly job and its parsing work — one adapter, no
   new vendor.
2. **Catalog schema:** `ticker`, `short_name`, `type` (`STOCK` / `FII` / `ETF` / `BDR`),
   `market` (`ROUND_LOT` / `FRACTIONAL`), `isin`, `last_seen`.
3. **Classification:** BDI `02` → STOCK, `12` → FII, `14` → ETF, `34` → BDR, `36` → BDR
   (ETF-backed BDRs, `…39`-suffix — mapping to confirm against the official layout PDF during
   implementation); BDI `96` / TPMERC `020` → FRACTIONAL market; small codes (`08`, `35`, `22`,
   `07`, `13`, `10`) ingest with `type = UNKNOWN` and a logged warning rather than a guess.
4. **Cadence:** daily, in the same run as the price sync (COTAHIST_D download), after ~21:00 BRT
   with retry, per #95.
5. **Union semantics:** upsert + `last_seen`; never delete — a delisted ticker must stay resolvable
   for historical positions and trades. "Current catalog" is a view over `last_seen`, not a
   truncated table.
6. **Fallback:** create-on-import stays for anything not in the catalog (illiquid ticker whose
   first trade is the import itself). brapi `/api/available` can be diffed opportunistically to
   flag gaps.
7. **If full legal names/segments are ever needed:** revisit the undocumented B3 proxies as a
   separate research spike — not required by the import slice.

## Open uncertainties

- BDI `36` mapping (121 records, ETF-backed BDRs) is inferred from samples, not read off the
  official layout PDF; same for the small codes.
- COTAHIST lists only instruments that **traded that day**: a listed-but-illiquid ticker may be
  absent from the catalog until its first trade — covered by create-on-import, but worth knowing.
- Short names are truncated at 12 chars; full names would need another source.
- brapi `/available` coverage was not diffed ticker-by-ticker against COTAHIST.
- The undocumented B3 proxies may exist and be stable; they were not found in this time-box.

## Sources

- COTAHIST file and layout: B3 Cotações Históricas page (URLs and observations recorded in
  `docs/research/2026-09-14-quote-api-b3.md`)
- brapi.dev — `/api/available` (probed live 2026-09-16)
- B3 — Empresas Listadas / Lista de Instrumentos pages (probed 2026-09-16; proxies not reachable)
