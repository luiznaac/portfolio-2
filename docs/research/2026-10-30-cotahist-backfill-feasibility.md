# Can historical COTAHIST closes back the pre-planning value series?

- **Wayfinder ticket**: [Research: can historical COTAHIST closes back the pre-planning value series?](https://github.com/luiznaac/portfolio-2/issues/296)
- **Map**: [Wayfinder map: make an imported B3 export usable end-to-end](https://github.com/luiznaac/portfolio-2/issues/287)
- **Date**: 2026-10-30
- **Method**: direct HTTP probes against B3 and BCB from the host (`curl`, `Accept: */*`) plus a line-scan of every annual file; repo code read at `eafb068`.

## Verdict

**Feasible for the value series; the benchmark is the gap.** B3 publishes an annual `COTAHIST_A<yyyy>.ZIP` for every year 2010-2026 (2018-2026 verified end-to-end), with the same 245-char fixed-width layout as the daily file, carrying Ações, units, FIIs, BDRs and ETFs in the spot market (TPMERC `010`). A 2018 -> 2026 backfill is ~520 MB compressed / ~4.4 GB uncompressed / ~2.30 M spot rows; at ~12 MB/s that is ~45-60 s of download plus seconds of line-scan (6.7 s in a Node probe), so the cost is one-time and modest.

Two pieces of the current code do not carry over as-is:

1. `CotahistGateway` only knows the daily URL, and its unzip reads the whole decompressed entry into one `ByteArray` and then a `String`. The 2026 annual entry is 827 MB, so that path would need ~2.5 GB peak and risks OOM. A year fetch needs a streaming line reader and a year URL.
2. `CotahistParser` reads ISIN from the wrong offset: the official layout puts CODISI at 1-based 231-242, the parser uses 225-236 (and its test fixture/comment repeat the wrong offset). On a real 2026 line the correct ISIN is `BRPETRACNPR6`; the parser stores `000000BRPETR`. Classification by BDI + ESPECI is unaffected, but anything keyed on ISIN (e.g. ticker-change remapping) is not trustworthy until this is fixed.

`BCB SGS` covers CDI and IPCA for the whole span for free, but **not IBOV**: SGS series 7 stops at 2019-09-30 and no BCB replacement exists. The pre-planning *value* series is backable from COTAHIST; the *IBOV benchmark* is not (see "Benchmarks" below).

## Dataset shape

One row per spot quote. All annual files are one ZIP entry named `COTAHIST_A<yyyy>.TXT`; records are 245 chars, US-ASCII; TIPREG `01` is a quote, `00` the header, `99` the trailer.

| Year | ZIP (MB) | Uncompressed (MB) | Lines | Spot rows | Trading dates | Distinct tickers |
| --- | --- | --- | --- | --- | --- | --- |
| 2018 | 19.5 | 143.3 | 580,157 | 125,626 | 245 | 843 |
| 2019 | 26.7 | 192.5 | 779,231 | 136,611 | 248 | 1,036 |
| 2020 | 37.2 | 309.2 | 1,251,648 | 187,306 | 249 | 1,584 |
| 2021 | 49.7 | 452.5 | 1,831,864 | 277,399 | 247 | 1,832 |
| 2022 | 58.6 | 523.0 | 2,117,442 | 308,865 | 250 | 2,087 |
| 2023 | 70.2 | 557.6 | 2,257,426 | 307,936 | 248 | 2,136 |
| 2024 | 79.3 | 651.0 | 2,635,563 | 322,397 | 251 | 2,230 |
| 2025 | 89.1 | 784.1 | 3,174,700 | 335,874 | 250 | 2,330 |
| 2026* | 89.5 | 827.3 | 3,349,341 | 295,216 | 207 | 2,342 |
| **2018-2026\*** | **519.6** | **4,440.4** | **17,977,372** | **2,297,230** | **2,195** | — |

\* 2026 is partial: the file runs through 2026-10-29, the latest published day. A full 2026 would be ~349 k spot rows and ~2.35 M for the span.

Fields the parser already reads, all present in every spot row: DATPRE 3-10 (`yyyyMMdd`), CODBDI 11-12, CODNEG 13-24, NOMRES 28-39, ESPECI 40-49, PREULT 109-121 (2 implied decimals), CODISI 231-242. Kinds present in TPMERC `010` (verified on 2026 rows): stock BDI `02` (VALE3), unit BDI `02` + ESPECI `UNT` (TAEE11), FII BDI `12` (HGLG11, KNRI11), ETF BDI `14` (BOVA11), BDR BDI `34` (AAPL34); BDI `35`/`36` also occur (35 maps to `UNKNOWN` by design). Fractional rows (TPMERC `020`, BDI `96`) and options (`070`/`080`) are present in the files and dropped by the parser's spot filter, as designed; `normalizedTicker()` strips a trailing `F`, so they would not collide anyway.

## Volume and cost

- **Full span (2018 -> 2026 partial)**: 519.6 MB compressed, 4,440 MB uncompressed, 17.98 M lines scanned, 2.30 M spot rows kept.
- **Download**: 79.3 MB in 6.85 s for the 2024 file (~11.6 MB/s), so the full span is ~45 s at that rate.
- **Unzip**: 0.47 s for 2018 (143 MB) and 2.53 s for 2026 (827 MB).
- **Line-scan**: 0.25 s (2018) to 1.18 s (2025) in a Node scan of all record types; 6.7 s total. A JVM streaming parse is the same order — tens of seconds at most.
- **Storage in `quote`** (already keyed `(ticker, date)` unique, no schema change): 2.30 M rows at roughly 100-200 B/row including the unique index is **~0.3-0.5 GB** if every spot row is stored. Filtered to the ledger's tickers it is tiny: ~20 tickers x 2,195 dates = ~44 k rows (~5-9 MB), 50 tickers ~110 k rows (~11-22 MB); month-end closes only for 20 tickers over 105 months is ~2.1 k rows (<1 MB).
- **Write path**: `QuoteRepository.upsertAll` uses one Exposed `batchUpsert`. A 336 k-row year (or 2.3 M rows overall) should be chunked per year and in batches (e.g. 1,000-5,000 rows per transaction) rather than one giant transaction.

## The exact fetch/parse path

```
GET https://bvmf.bmfbovespa.com.br/InstDados/SerHist/COTAHIST_A{year}.ZIP
  Accept: */*
```

The annual fetch is behind the same B3 content-negotiation gate as the daily one: `Accept: application/json` -> 406 (98 B HTML), `Accept: application/zip` -> 406, `Accept: */*` -> 200. The `Accept: */*` fix from [#288](https://github.com/luiznaac/portfolio-2/issues/288) is still unimplemented in `CotahistGateway`, and it must be applied per request to the year fetch too.

Other granularities exist and work: monthly `COTAHIST_M{MM}{yyyy}.ZIP` (e.g. `M012024` = 6.28 MB) and daily `COTAHIST_D{ddMMyyyy}.ZIP` (e.g. `D29102026` = 591,800 B compressed / 4.15 MB uncompressed / 1,442 spot rows).

Parse path:

- Stream the ZIP entry: `ZipInputStream(ByteArrayInputStream(body))` -> `BufferedReader(InputStreamReader(zip, Charsets.US_ASCII))` -> `lineSequence()`. Do **not** use `zip.readBytes().decodeToString()`: that materialises the whole 827 MB entry plus a ~1.65 GB UTF-16 `String` for 2026.
- Keep rows with TIPREG `== "01"`, TPMERC `== "010"` and a parseable PREULT; drop header/trailer, options, fractional and the term market.
- Map to `Quote(ticker = normalizedTicker, date = DATPRE, close = PREULT.movePointLeft(2), source = COTAHIST)` and upsert. Re-running a year is idempotent (the unique key overwrites; COTAHIST is authoritative).
- Surface non-200 and parse failures instead of the current `null`/`runCatching { ... }.getOrNull()` (a missing or blocked year must not look like an empty series).
- No schema change: `quote` is already `(ticker, date)` unique.

## Coverage holes and the on-or-before lookup

- **Years**: annual files exist for 2010-2026 (2010 10.4 MB, 2012 11.9, 2014 13.7, 2015 14.1, 2016 15.7, 2017 16.5); the official page says the archive reaches back to 1986. 2018 -> now has no missing year.
- **No records on weekends/holidays**: a date with no trading simply has no rows. A suspended ticker has no rows for the suspended days.
- **On-or-before is the right lookup**: `PerformanceService.valueAt` already does `fetchAllBy(ticker).lastOrNull { it.date <= date } ?: averagePrice`, so a month-end that is a weekend or holiday takes the last trading day's close. The `?: averagePrice` is a pre-quote guard, not a market mark.
- **Delisted/renamed tickers**: the on-or-before lookup returns the last available close indefinitely. A held delisted ticker without an EXIT trade would be marked at its last close, not zero. The stable key for a rename is the ISIN, which is corrupt in the parser today (above); fix it before relying on remapping. This is a product edge, not a source-feasibility blocker.
- **Unadjusted closes**: PREULT is the raw last trade, not split/dividend-adjusted. "replay x close" is only correct if the replay applies corporate actions at their ex-dates (D8/D9); the quote source cannot supply that.

## Benchmarks: what BCB SGS actually covers

`PerformanceService.benchmarkObservations(from, to)` fetches each `BenchmarkIndex` by range.

- **CDI, SGS 12** (daily % rate): 2,217 rows from 2018-01-02 to 2026-10-29 — covers the span.
- **IPCA, SGS 433** (monthly % variation): 105 rows from 2018-01 to 2026-09 — covers the span.
- **IBOV, SGS 7** (daily index level): 432 rows from 2018-01-02 to **2019-09-30** (last 104,745), then the series stops. No BCB replacement was found (the open-data portal returns no dataset for "ibovespa"), so BCB SGS does **not** cover the same span for free.

API range behaviour matters for the gateway: a range that overlaps the series end returns HTTP 200 truncated at 2019-09-30; a range entirely after it returns HTTP 404 with an empty body. `BcbSgsGateway` maps 404 to `emptyList()`.

Consequence in `BenchmarkCalculator.levelReturn`:

- If the fetch range starts before 2019-09-30, the truncated series makes base and end both 2019-09-30 for every later month -> **0.00%** silently.
- If the range starts after it (the map's 2020-03 -> 2026 scenario), 404 -> empty -> the IBOV contribution is null and the blend renormalises -> the Ações/FII benchmark is **absent**.

Either way the Ações/FII benchmark is wrong or absent after 2019-09. The value series is unaffected; the benchmark needs a source decision (filed as a follow-up ticket on the map).

## Build implications (not decisions)

- Add a year/range fetch to `ICotahistSource` and `CotahistGateway`; keep `fetchTradingDay()` for the current quote.
- Apply `Accept: */*` per request on both the daily and year paths.
- Stream the ZIP; never materialise a whole year entry.
- Fix CODISI to 0-based `230 until 242` (1-based 231-242) in `CotahistParser` and in `CotahistFixtures`/`CotahistParserTest`; consider raising `MIN_RECORD_LENGTH` to 245.
- Chunk the upserts per year and in batches; filter to ledger tickers when only history is needed (drops storage from ~0.3-0.5 GB to ~5-20 MB).
- No migration: `quote` is already `(ticker, date)` unique.

## Evidence

- `curl -sSI -H 'Accept: */*' https://bvmf.bmfbovespa.com.br/InstDados/SerHist/COTAHIST_A2024.ZIP` -> 200, 79,261,198 B; `unzip -l` -> one entry, 651 MB.
- Node line-scan of every annual file produced the table above (`probe.mjs`: TIPREG `01`, TPMERC `010`, numeric PREULT).
- `curl 'https://api.bcb.gov.br/dados/serie/bcdata.sgs.7/dados?formato=json&dataInicial=01/01/2018&dataFinal=30/10/2026'` -> 200, 432 rows, last 2019-09-30; the same call with a range starting 2020-01-01 -> 404.
- Real 2026 PETR4 spot line: `line[230:242]` = `BRPETRACNPR6` (official CODISI), `line[224:236]` = `000000BRPETR` (what the parser stores).
- Official layout: [SeriesHistoricas_Layout.pdf](https://www.b3.com.br/data/files/33/67/B9/50/D84057102C784E47AC094EA8/SeriesHistoricas_Layout.pdf) (rev 2.0, 2020-10-05), 245-byte records, CODISI 231-242, TPMERC 010 vISTA / 020 fracionario / 070-080 opcoes.
