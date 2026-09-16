---
source: luiznaac/portfolio-2#118
branch: research/kinvo-extrato
date: 2026-09-16
---

# Research: Kinvo extrato-carteira exports

- **Ticket:** [luiznaac/portfolio-2#118](https://github.com/luiznaac/portfolio-2/issues/118) — "Research: Kinvo extrato-carteira exports"
- **Date:** 2026-09-16
- **Branch:** `research/kinvo-extrato`
- **Question:** What are the files in `references/kinvo-extrato/`? Which Kinvo screen/report produced them, and what fields do they carry — positions (quantity, average price), asset classes, proventos? Do they change the closed conclusion of "Research: Kinvo export for initial positions" (no positions export; only a Premium, web-only IR auxiliary XLSX)? If they do carry positions, does "Kinvo as an ongoing source of balances" deserve a re-scope on the map?

## Answer (short)

- **The two files are filtered exports of Kinvo's Extrato (portfolio statement): movements, not positions.** One row per recorded movement (`Aplicação` = purchase / `Resgate` = sale), carrying ticker, institution, date, unit price, quantity and total. No current quantity per ticker, no average price, no balances, no proventos, and no asset classes beyond what the export was filtered to.
- **`kinvo--extrato-carteira 15_09_2026 3_25.xlsx`** is 42 `Aplicação` rows at **RICO** (10–11/08/2026, 33 distinct tickers); **`kinvo--extrato-carteira 15_09_2026 3_26.xlsx`** is 11 `Resgate` rows at **XP Investimentos** (06–11/08/2026, 6 tickers). They were exported one minute apart (15/09/2026 03:25 and 03:26) — two filter combinations of the same statement. Everything in both files is `Tipo = Ação`; the `Câmbio` and `Conexão` columns are empty throughout.
- **No average price anywhere.** `Valor` is the per-unit price of the movement (verified: `Valor × Quantidade = Valor Total` in all 53 rows). `Custo` — populated only on the XP rows, constant per date batch (R$ 4.90 for 10–11/08, R$ 8.60 for 06/08, shared across different tickers) — is consistent with an operation/corretagem cost, not an average cost.
- **The producing feature is Kinvo's Extrato** (Kinvo Web statement of movements; available on the Free plan, marked in the plans page as considering all portfolio assets, not just the 10 free-tier assets). The **export itself is undocumented** — no help-center article covers it; the only documented Excel export remains the IR auxiliary report from #96.
- **Ticket #96's conclusion stands**: there is still no positions export (no current quantity, average price or balance), and this export carries no renda fixa, no proventos. The new fact is that Kinvo does expose a working, filterable **movements** XLSX export (undocumented), overlapping with the B3 Área do Investidor Negociação export (#94) for broker-connected movements.
- **Map re-scope answer: no.** "Kinvo as an ongoing source of balances" stays out of scope — the export carries no balances. A movement-level cross-check is a different, unasked question (see Open uncertainty).

## Evidence

### 1. The files themselves (primary evidence)

Inspected 2026-09-16 with openpyxl (Python 3.13) directly from `references/kinvo-extrato/`; all figures below are computed, not eyeballed. Neither file contains account identifiers — only broker names, tickers and trade data.

Common shape: single visible sheet `Extrato`, 11 columns, no merged cells, no defined names; workbook properties `creator = Unknown`, `created == modified` equal to the export timestamp in the filename.

| Column | Meaning (observed) | Populated? |
|---|---|---|
| `Data` | movement date, `dd/mm/yyyy` string | always |
| `Produto` | `TICKER - NAME`, sometimes with the B3 suffix (e.g. `ALOS3 - ALLOS       ON      NM`) | always |
| `Tipo` | Kinvo's asset class (`Ação` in both files) | always |
| `Descrição` | movement kind: `Aplicação` / `Resgate` | always |
| `Instituição` | broker (`RICO`, `XP Investimentos`) | always |
| `Conexão` | connection name (connections were apparently unnamed) | never in these files |
| `Valor` | price per unit of the movement — **not** an average price | always |
| `Quantidade` | quantity moved | always |
| `Custo` | operation cost; see analysis above | XP rows only |
| `Câmbio` | FX rate (for international operations) | never in these files |
| `Valor Total` | `Valor × Quantidade`, exact in all 53 rows | always |

File summaries:

- `kinvo--extrato-carteira 15_09_2026 3_25.xlsx` — 42 rows, all `Ação` / `Aplicação` / `RICO`; dates 11/08 (11 rows) and 10/08 (31 rows); 33 distinct tickers: BBDC4, ALOS3, CURY3, DIRR3, ENGI11, ITUB4, TEND3, ALUP11, ITSA4, VALE3, CPLE3, VIVA3, VIVT3, ORVR3, TOTS3, TTEN3, TUPY3, LREN3, LWSA3, PRIO3, PRNR3, SBSP3, GGBR4, IGTI11, ECOR3, EMBJ3, CXSE3, AXIA3, BMOB3, BPAC11, BRBI11, CEAB3, CSMG3. `Custo` empty in all 42 rows.
- `kinvo--extrato-carteira 15_09_2026 3_26.xlsx` — 11 rows, all `Ação` / `Resgate` / `XP Investimentos`; dates 11/08 (3), 10/08 (6), 06/08 (2); tickers B3SA3, ITSA4, ALUP11, CPLE3, EGIE3, EZTC3. `Custo` = 4.90 (9 rows) / 8.60 (2 rows).

Cross-check: the same instruments appear as buys in one file and sells in the other (ALUP11, CPLE3, ITSA4 on 10/08) — consistent with the user's actual operations, and not with a generated position snapshot.

### 2. Which feature produced them: the Extrato statement

- Kinvo Web's **Extrato** is a filterable statement of movements recorded over connected brokers. The sheet name (`Extrato`), the filename template (`kinvo--extrato-carteira`) and the column set match it exactly.
- Plans page (<https://consolidador.kinvo.com.br/planos/>, fetched 2026-09-16): in the comparison table, **Extrato** renders with a check on **both Free and Premium** columns (`<i class="fas fa-check-square">` inside both `colunafree` and `colunapremium`), and the Free cell carries the asterisk footnote "*considera todos os ativos da carteira, não restrito a 10 ativos*" — i.e. the statement considers all portfolio assets, not only the 10 free-tier ones. The same table renders **Resumo de Posições para Imposto de Renda** and **Relatórios em PDF e Excel** as `fa-times` on Free and `fa-check-square` on Premium, matching #96.
- **No help-center article documents exporting the extrato:** the full solutions index (suporte.kinvo.com.br/solutions, fetched 2026-09-16) has no Extrato folder and no export article; the legacy help desk search for "extrato" returns only connection FAQs, Premium and Debênture articles (fetched 2026-09-16); #96's full-text scan of the help center's bundled article data found "Exportar em excel" only in the discontinued Kinvo2B article.

### 3. What this does to #96

#96 concluded: no general XLSX/CSV export of **current positions** exists; the only documented Excel export is the Premium, web-only IR auxiliary XLSX (positions as of 31/12, renda variável + cripto only, at average acquisition cost). Both files inspected here are **not positions**, so that conclusion is untouched. One addition: a filterable **movements** XLSX export does exist (undocumented; built on a Free-tier feature), useful as a cross-reference for broker-connected movements — without consistent fees, without proventos, and only for entities connected inside Kinvo.

## Field mapping (if ever used as an auxiliary movements source)

| Kinvo `Extrato` | App movement field | Notes |
|---|---|---|
| `Data` | trade date | `dd/mm/yyyy` strings |
| `Produto` | ticker + display name | ticker is the prefix before ` - ` |
| `Tipo` | asset class hint | Kinvo's taxonomy; only `Ação` seen here |
| `Descrição` | side | `Aplicação` → buy, `Resgate` → sell |
| `Instituição` | broker / custodian | |
| `Valor` | unit price | movement price, **not** average price |
| `Quantidade` | quantity | |
| `Custo` | operation cost (semantics unverified) | XP rows only in these samples |
| `Câmbio` | FX rate (international) | empty here |
| `Valor Total` | gross value | exact `Valor × Quantidade` |

What it cannot provide: current quantity per ticker, average price, balances, unfiltered asset classes, proventos, fee completeness across brokers.

## Open uncertainty

- **Which surface hosts the export button** — the Extrato screen itself (Free, as the content suggests) or the undocumented Premium "Relatórios em PDF e Excel" area (#96, checklist item 3). Either way the content is the movement statement. The user's account tier at export time is unknown.
- The exact filter combination and period used per file is inferred (institution × movement type); the user can confirm from the screen.
- `Custo` semantics (corretagem per nota? per-operation fee? something else) — the per-batch constants shared across different tickers rule out average cost, but do not identify the field.
- Whether the Extrato export is officially supported (it is undocumented and may change), and whether classes like FIIs and renda fixa actually appear in other portfolios' extrato (likely, but not evidenced by these samples).
- Movement-level cross-check feasibility (Kinvo Extrato vs B3 extract) was not asked and not analysed; if wanted, it is a separate question.
