---
source: luiznaac/portfolio-2#94
branch: research/b3-area-do-investidor
date: 2026-09-15
---

# Research: B3 Área do Investidor exports — negociações + posição em custódia

- **Ticket:** [luiznaac/portfolio-2#94](https://github.com/luiznaac/portfolio-2/issues/94) — "Research: parse B3 Área do Investidor exports"
- **Date:** 2026-09-15
- **Branch:** `research/b3-area-do-investidor`
- **Question:** Which exports does B3's Área do Investidor provide (negociações/extrato, posição em custódia), in what formats (CSV/XLSX/PDF), with which fields — and can they (a) feed the movements ledger and (b) seed initial positions?

## Answer (short)

- **A custody/position export exists: the "Relatório Consolidado".** B3's own material says: *Menu → "Relatórios" → "Relatório Consolidado"*, period *"Anual"*, choose **PDF or Excel**; it contains *"a sua posição, proventos recebidos (dividendos, juros sobre capital próprio e rendimentos) e os reembolsos de empréstimos de ativos"*; the Excel download also shows the **issuer CNPJs** of the assets. This is the seed source the Kinvo research could not find.
- **Trade exports are XLSX and trade-only.** The repo sample (`references/movimentacoes_B3.xlsx`, sheet `Negociação`) is the *Negociação / histórico de negociações* dataset: 82 rows, 9 columns, `Compra`/`Venda`, full + fractional market, **no fees, no trade id, no asset class, no time, no CNPJ**.
- **A second, broader movement export exists** — "Movimentação" (under *Extratos e Informativos*), Excel, filtered by a date range. B3's site describes this dataset as covering "aplicações, **eventos** e vencimentos"; secondary guides add transferências and corporate events (splits/grupamentos). It is not the same dataset as the sample.
- **Movements ledger:** the Negociação export can feed it for buys/sells (date, side, institution, ticker, quantity, unit price, gross value). **Costs (corretagem, emolumentos, ISS, IRRF) are absent from the B3 export** — they exist only in the broker's nota de corretagem or the user's records.
- **Proventos:** present in the Relatório Consolidado (dividendos, JCP, rendimentos, reembolsos) — B3 *can* source proventos, but the per-event schema is not published; verify in-account.
- **Dedup:** no trade id ⇒ natural composite key `(institution, trade date, normalized ticker, side, quantity, unit price)` **plus an occurrence ordinal per key** (same-day partial fills are common — 6 duplicate `(date, side, ticker)` groups in the sample), and file-level idempotence via a canonical row hash. Never key the asset on the ticker *with* the fractional `F` suffix.
- **Seed strategy:** generate the Relatório Consolidado Excel for the **month-end closest to go-live** (confirm the monthly option exists in-account), seed positions from it, then replay the movements export for everything after that snapshot date. Fallback: reconstruct from full movement history (2019+) and enter pre-history plus non-B3 assets (RF/caixa) manually.

## 1. The sample file — verified structural read

File in repo: `references/movimentacoes_B3.xlsx` (12,319 bytes). **Note:** the ticket text says `movimentacoes.xlsx`; the actual repo file is `movimentacoes_B3.xlsx`. All numbers below were verified by parsing the actual file with `openpyxl` 3.1.5 / zip inspection (2026-09-15).

- **One sheet:** `Negociação` (accented, visible). Range `A1:I83` = header + **82 data rows**, 9 columns.
- **Header (exact, row 1):** `Data do Negócio`, `Tipo de Movimentação`, `Mercado`, `Prazo/Vencimento`, `Instituição`, `Código de Negociação`, `Quantidade`, `Preço`, `Valor` — all stored as shared strings.
- **Cell model (from `xl/worksheets/sheet1.xml`):** text columns use `t="s"` (shared strings); `Quantidade`/`Preço`/`Valor` are **native numeric cells** (e.g. `<c r="G2" s="4"><v>7.0</v></c>`). **Dates are text** (`dd/MM/yyyy` style on a string cell) — e.g. `A2` is shared-string index 9 = `11/08/2026`, not an Excel serial date.
- **Value domains:**
  - `Tipo de Movimentação`: `Compra` (59), `Venda` (23) — only these two.
  - `Mercado`: `Mercado Fracionário` (53), `Mercado à Vista` (29). In this sample the correlation is perfect: every `F`-suffixed ticker is fractional, every non-`F` ticker is à vista.
  - `Instituição`: `XP INVESTIMENTOS CCTVM S/A.` (all 82 rows).
  - `Prazo/Vencimento`: `-` on 76 rows; on 6 rows it equals the trade date itself (rows 11, 15, 22, 55, 56, 57). No row-level meaning is documented; do not treat it as a settlement date.
  - `Quantidade`: whole numbers stored as floats (`7.0`, `200.0`, `312.0`); never fractional, no zeros, no blanks.
  - `Preço`: BRL unit price, 2-decimal floats (`26.09`, `31.56`, …; min 3.88, max 153.04).
  - `Valor`: gross BRL amount as a float, including `.0`-terminated values (`2854.0`).
- **Accounting invariant:** `Valor == Quantidade × Preço` **exactly for all 82 rows** (0 mismatches at 0.005 tolerance). The file therefore carries **gross notional only — no corretagem, emolumentos, ISS or IRRF**, and `Valor` is always positive (direction comes from `Tipo de Movimentação`).
- **Dates:** 06/08/2026 (7 rows), 10/08/2026 (61), 11/08/2026 (14) — a 4-calendar-day window with exactly 3 trading days. Rows are sorted **date descending, then ticker ascending** — not chronological by execution.
- **Tickers:** 63 distinct trade codes → 58 distinct underlying assets after stripping the trailing `F`. 5 assets traded in both markets (`ALUP11`, `B3SA3`, `CPLE3`, `EGIE3`, `ITSA4`); 31 fractional-only codes. Real-world variety present: ações (`B3SA3`, `ITSA4`), FIIs (`BRCO11`, `KNRI11`, `MXRF11`), FII fractionals (`ENGI11F`, `BRBI11F`, `IGTI11F`), a BDR (`ROXO34`), a unit (`ALUP11`). **No class column exists**, and the numeric suffix is not a reliable class discriminator (`11` covers FII/ETF/unit; `34` = BDR).
- **No trade id, no order id, no execution time, no CNPJ, no asset name.** No blank cells anywhere. **No exact duplicate rows.** Full-row duplicates do not exist, but duplicate `(date, side, ticker)` groups do (see §4).
- **Workbook package:** no `docProps/core.xml`; theme named `"Sheets"`; an empty `xl/drawings/drawing1.xml` and `xl/persons/person.xml`. This is consistent with a server-side spreadsheet generator rather than Excel-authored files (observation, not independently confirmed) — a hint that field/format drift across versions is possible.

**Prior-exploration claims — verified:** one sheet `Negociação`; 82 data rows; header exact; `Compra`/`Venda`; `Mercado Fracionário`/`Mercado à Vista`; `XP INVESTIMENTOS CCTVM S/A.`; tickers `B3SA3`, `B3SA3F`, `BRCO11`, `ROXO34`, `ALUP11`; dates 06/08/2026–11/08/2026. **New findings:** dates/strings are text cells; no fee column and the `Valor = Qtd × Preço` invariant; `Prazo/Vencimento` anomaly; no id; duplicate natural-key groups; row ordering.

## 2. Which exports the Área do Investidor provides

B3's official product page lists the datasets available in the Área do Investidor (fetched 2026-09-15): *"extratos de posições e movimentações … Portfolio de investimentos; Rendimentos (por exemplo: dividendos); Extratos (Listado, Balcão e Tesouro Direto); Empréstimo de títulos; Garantias; Informe de rendimento e reembolso de empréstimos de títulos; **Histórico de negociações**; Aviso de transferências; **Movimentações (aplicações, eventos e vencimentos)**"*. Source: <https://www.b3.com.br/pt_br/produtos-e-servicos/central-depositaria/canal-com-investidores/area-do-investidor/>.

| Export / dataset | Where | Format | Granularity | What it carries | Source strength |
|---|---|---|---|---|---|
| **Relatório Consolidado** | Menu → `Relatórios` | **PDF or Excel** | period **"Anual"** (B3 2023 article also mentions "mês a mês" — needs in-account check) | **Position** (last business day of the period), **proventos** (dividendos, JCP, rendimentos), loan reimbursements; Excel includes **issuer CNPJs** | Official: B3 flyer, B3 help-desk article, Bora Investir |
| **Movimentação** | `Extratos e Informativos` → `Movimentação` | **Excel** ("Arquivo em Excel para ser importado em planilhas") | user-selected **date range** (start/end + FILTRAR), download button `BAIXAR` | transactions incl. events/vencimentos; secondary guides add transferências, splits, grupamentos | Official landing page names the dataset; the download flow is documented only by **secondary** guides |
| **Negociação / Histórico de negociações** | Negociação view (exact menu path not documented publicly) | **XLSX** | date range (the sample spans a few days) | the sample: buys/sells only, 9 columns | Official landing page names the dataset; repo sample verifies the schema |
| **Informes – Empréstimos** | Menu → `Relatórios` → `Informes - Empréstimos` | PDF (annual inform) | annual | income report for lent (doadora) assets only | Official flyer/help material |
| **Extratos (Listado, Balcão, Tesouro Direto)** | `Extratos e Informativos` | not documented | not documented | consultable extracts | Official landing page only |
| **APIs** (Posição, Movimentação, Negociação de Ativos, Eventos Provisionados, Garantias, Ofertas Públicas, API Guia) | B3 For Developers | JSON APIs | **D-1**, data from **01/11/2019** | same domains as the exports, programmatically | Official Manual Técnico (version history up to 10/06/2026) |

**Positions export — details and limits.** The official flyer "IRPF 2025: Suas informações na Área do Investidor" (B3, hosted at `atendimento.b3.com.br`) states verbatim: *"'Menu' > 'Relatórios' > 'Relatório Consolidado'. Em 'Relatório Consolidado', selecione o período 'Anual' e escolha entre gerar o relatório em PDF ou Excel. Posição último dia útil do ano, dividendos, juros sobre capital próprio (JCP), rendimentos e reembolso recebidos"*, plus *"Ao fazer o download em formato Excel o investidor visualiza a relação de CNPJs das empresas emissoras dos seus ativos."* A B3 help-desk article repeats the path and adds *"Neste relatório você encontrará a sua posição, proventos recebidos (dividendos, juros sobre capital próprio e rendimentos) e os reembolsos de empréstimos de ativos."* B3's editorial site (Bora Investir, 2025) adds the asset coverage: *"todas as suas posições em ações, ETFs, BDRs, CDBs, Tesouro Direto e outros ativos registrados e negociados na B3"*, and says the document *"contém o histórico de dividendos, juros sobre capital próprio (JCP), rendimentos e reembolso recebidos de suas posições no ano"*.

Caveats: (a) B3 labels all Área do Investidor reports *"para fins consultivos"* — official tax documents come from the broker/custodiante; (b) **no public source documents the Relatório Consolidado's columns** (quantity? average cost? closing price? CNPJ only?); (c) the older Bora article (2023) says the report can be generated *"mês a mês ou ano a ano"*, which — if still true — allows a month-end snapshot instead of a stale year-end one; (d) the 2025 flyer and 2025/2026 help article describe only "Anual", so this must be checked in-account.

**Is there a position export *outside* Relatório Consolidado?** Nothing public documents one. The "Portfolio de investimentos" and "Extratos" screens are consultable; whether they have their own download is unverified. The only confirmed position-bearing download is the Relatório Consolidado.

**APIs are not a consumer alternative.** The official Manual Técnico states the Movimentação API returns *"Dados até D-1 das transações ocorridas nas contas do investidor em um determinado período"*, Posição returns *"Dados até D-1 do saldo de investimentos na conta do investidor"* (endpoints: Empréstimo de Ativos, Tesouro Direto, Renda Fixa, Derivativos, Renda Variável), and Negociação de Ativos returns *"todas as compras e vendas do investidor"*. Data starts **01/11/2019**, *"Apenas pessoas jurídicas poderão contratá-lo"*, self-assessment by B3, investor consent required, and the current price list has a **R$ 500/month floor** (R$ 6,000/year) below 10k consenting investors. Not viable for a personal app; useful only as a reference for what the underlying model contains.

**No CSV or JSON user-facing export is documented anywhere; XLSX/PDF only** (the sample is XLSX).

## 3. Proventos and corporate actions

- **Proventos:** confirmed present in the **Relatório Consolidado** — *"histórico de dividendos, juros sobre capital próprio (JCP), rendimentos e reembolso recebidos"* (Bora Investir, 2025) and *"proventos recebidos (dividendos, juros sobre capital próprio e rendimentos)"* (B3 help article). The Área do Investidor also has a dedicated proventos view — *"uma aba que unifica todas as informações referentes aos proventos, como dividendos, juros sobre capital próprio e garantias"* (Bora Investir, 2023). **No source documents a standalone proventos download schema** (dates, values, type, per-asset split); the consolidated report's proventos are described as a history within the report.
- **Corporate actions:** B3's own landing page says the **Movimentações** dataset covers *"aplicações, eventos e vencimentos"* — "eventos" is the corporate-action umbrella. The API domain includes an *"Eventos Provisionados"* dataset: *"Dados até D-1 dos eventos corporativos de renda variável provisionados por investidor"*. A secondary guide (Cota B3) states the Movimentação XLSX includes *"eventos corporativos (splits, grupamentos) e transferências"*. **The Negociação sample contains none of this** — it is trades only. So: corporate actions are *not* in the trade export; they must be sourced from the Movimentação export (verify its `Tipo de Movimentação` domain in-account) or from the broker.
- **Ações vs FIIs:** both appear in the Negociação export (full tickers). The export has no class column, so classification requires a ticker reference table; the Relatório Consolidado reportedly groups by asset class implicitly (all classes above).

## 4. Dedup — stable keys and pitfalls

There is **no trade id, no order id, no execution sequence, no timestamp** in the sample. Dedup must be content-based.

- **Natural key:** `(Instituição, Data do Negócio, ticker-normalized, Tipo de Movimentação, Quantidade, Preço)` is unique across the 82 sample rows (0 duplicates at this width). But it is **not guaranteed unique in general**: two same-day executions of the same asset, same side, same quantity and same price can legitimately occur.
- **Occurrence ordinal:** to be safe, key = natural key + `ordinal` (1st/2nd/… occurrence of that key), assigned in a deterministic, file-independent order (e.g. the file's row order, which is stable for the same query). This is the closest thing to a stable surrogate the format allows.
- **Sample duplicate evidence (keys *without* qty/price):** six `(date, side, ticker)` groups repeat because an order filled in multiple executions:
  - `11/08/2026 Venda B3SA3F` ×2 (6 @ 14.26; 66 @ 14.54)
  - `10/08/2026 Compra HGBS11` ×2 (34 @ 18.86; 20 @ 18.86 — **same price, different quantity**)
  - `10/08/2026 Venda HGRU11` ×2 (4 @ 116.97; 7 @ 117.00)
  - `10/08/2026 Compra ORVR3F` ×2 (8 @ 67.29; 6 @ 67.32)
  - `06/08/2026 Venda EGIE3F` ×3 (12 @ 29.91; 13 @ 29.94; 2 @ 29.93)
  - `06/08/2026 Venda EZTC3F` ×3 (65 @ 11.03; 20 @ 11.04; 6 @ 11.05)
  These must **not** be collapsed — each is a distinct execution (and B3 itself chose not to aggregate).
- **File-level idempotence:** compute a canonical hash over the parsed rows (sorted, normalized decimals/tickers/encoding) and treat an identical file as already imported. This protects against the same period being downloaded twice.
- **Overlapping periods are expected:** exports are date-range based, so monthly/adhoc windows will overlap (e.g. Jan–Mar and Mar–Jun). Dedup the overlap with the natural key + ordinal, and reconcile counts per period boundary.
- **Pitfalls:**
  - **Fractional `F` suffix:** `B3SA3F` and `B3SA3` are executions of the *same fungible asset*; the F variant is a trade marker (fractional market), not a different security. Keying the asset on the raw code double-counts positions. Store the raw code + market for audit, normalize to the underlying ticker for the asset identity.
  - **Same-day repeat trades:** as above; a naive `(date, ticker, side)` key silently drops executions.
  - **Re-import after partial downloads:** row order is stable per query but a differently-bounded query can reorder rows within a day (order is ticker-ascending). The ordinal must be derived after sorting by the same deterministic rule on both sides, or use the full 6-field natural key + count reconciliation.
  - **Institution string drift:** normalize `Instituição` (case, punctuation, `S/A.` vs `S.A.`) before keying; a spelling change across exports would otherwise break dedup.
  - **Empty-string vs `-` placeholders:** normalize `Prazo/Vencimento` before hashing.
  - **Decimal canonicalization:** `2854.0` vs `2854.00` vs `2854` must hash the same — use fixed-scale `Decimal`, not text or binary float equality.

## 5. Mapping sketch for the import design

### 5.1 Negociação row → movements ledger record

| B3 column | Sample | Ledger field | Notes |
|---|---|---|---|
| `Data do Negócio` | `11/08/2026` (text) | `trade_date` | parse `dd/MM/yyyy`; it is the trade date, not settlement |
| `Tipo de Movimentação` | `Compra` / `Venda` | `side` / signed `quantity` | derive `+qty` for Compra, `−qty` for Venda; positive `Valor` |
| `Mercado` | `Mercado Fracionário` / `Mercado à Vista` | `venue` metadata | drives `F`-suffix normalization; keep for audit |
| `Prazo/Vencimento` | `-` (76/82) | — | discard; equals trade date when present; meaningless for spot |
| `Instituição` | `XP INVESTIMENTOS CCTVM S/A.` | `broker` | normalize before keying |
| `Código de Negociação` | `B3SA3F` | `asset` lookup + `raw_ticker` | strip trailing `F` for asset identity; keep raw code for audit |
| `Quantidade` | `7.0` | `quantity` | whole units; store as `Decimal`, not float/int cast |
| `Preço` | `26.09` | `unit_price` | BRL unit price |
| `Valor` | `182.63` | `gross_amount` | = qty × price; assert invariant on import |
| *(missing)* | | `fees`, `net_amount` | **manual** — corretagem/emolumentos/ISS/IRRF only in notas de corretagem |
| *(missing)* | | `asset_class` | **external reference table** (suffix insufficient: `11` = FII/ETF/unit, `34` = BDR) |
| *(missing)* | | `asset_cnpj` | not in this export; Relatório Consolidado Excel reportedly has issuer CNPJs |
| *(missing)* | | `movement_id` | synthesize: canonical row hash or natural key + ordinal |

**Minimum manual input per trade: costs.** Everything else in the ledger can be derived from the file. If the plan's ledger needs `net_amount` per trade, the importer needs a rules table (broker fee schedule) or user input — B3 never carries it.

### 5.2 Initial positions — what B3 can and cannot do

- **Can:** seed **listed assets** (ações, FIIs, ETFs, BDRs, units) with the **Relatório Consolidado Excel** — it is the only documented source that carries a position (`"sua posição"` on the last business day of the period; Excel includes issuer CNPJs) plus proventos received. B3's 2023 article also describes position-level `valor aplicado`, `valor líquido` and `preço de fechamento`, but **the exact export columns are unverified** — quantity and average cost may or may not be present.
- **Cannot (with current evidence):** seed renda fixa/fundos/caixa with a B3 *custody position* (the consolidated report reportedly includes CDBs/Tesouro Direto, but per the API manual, Balcão RF exposes only *"quantidade, ISIN, data de aquisição e vencimento"* — no prices); seed pre-2019 history (B3 base starts **01/11/2019** per the API manual; the portal itself reportedly shows history from 2019 per Cota B3); provide tax-grade documents (B3 reports are consultive).
- **Recommended seed path:**
  1. Generate the Relatório Consolidado **Excel for the most recent month-end available** (verify the monthly option in-account; if only "Anual", use the latest year-end and accept a longer replay).
  2. Seed positions from it; validate each seeded asset with a ticker reference table (class, CNPJ).
  3. Replay the movements/negociações exports from the **day after** the snapshot date to "today" to bring positions to current — the same dedup rules as §4.
  4. Cross-check the replay result against the Relatório Consolidado totals; any divergence means a missing export period or a corporate action.
  5. Enter RF/fundos/caixa and pre-2019 positions manually (Kinvo is reference-only — see `docs/research/kinvo-export.md` on `research/kinvo-export`).
- **Fallback if the Relatório Consolidado Excel proves unusable** (PDF-only columns, no quantity): reconstruct positions from the full movements history (2019+) — valid only if the portfolio's listed assets were all bought inside that window — else manual entry from the broker/Kinvo screens.

## 6. Failure modes to handle in the importer

| # | Failure mode | Evidence / source | Mitigation |
|---|---|---|---|
| 1 | Wrong file type (PDF instead of XLSX) | Relatório Consolidado offers PDF/Excel | Detect by extension + magic bytes, fail loudly |
| 2 | Encoding of accents (Negociação, Instituição, à Vista, Fracionário) | XLSX is UTF-8; a CSV path could be latin-1 | Parse OOXML directly; if CSV ever appears, detect and transcode |
| 3 | Dates as text, not Excel serials | `A2` is `t="s"` shared string `11/08/2026` | Accept both `str` and `datetime`; parse `dd/MM/yyyy` |
| 4 | `Prazo/Vencimento` placeholder `-` | 76/82 rows | Normalize placeholders to null; never map to settlement |
| 5 | Numeric columns as floats with `.0` (`2854.0`) | cell XML `G2=7.0`, `I4=2854.0` | Use `Decimal` on str(value), fixed scale; no equality on floats |
| 6 | Locale number formats if data is ever copy-pasted/CSV (`2.854,00`, `26,09`) | Not in the XLSX (native numeric cells); risk only on non-XLSX paths | Reject non-parseable numerics; never `float("2.854,00")` blindly |
| 7 | Fractional vs full ticker (`B3SA3` vs `B3SA3F`) | 5 dual-market assets in sample | Normalize to underlying ticker; keep raw + market |
| 8 | Same-day repeated executions | 6 duplicate `(date, side, ticker)` groups | Natural key + ordinal; never collapse by ticker+date |
| 9 | Overlapping re-exports | date-range granularity | File hash + per-key count reconciliation |
| 10 | Header/sheet drift between report types (`Negociação` vs `Movimentação` vs Relatório Consolidado) | Sample header ≠ Cota B3's described Movimentação columns (`Preço Unitário`, `Data`...) | Match by header signature; support per-report parsers; fail on unknown signature |
| 11 | Row order is not chronological (date desc, ticker asc) | Sample ordering | Sort by trade date after import; don't use file order as event sequence |
| 12 | Non-trade movement types (Transferência, Grupamento, Desdobramento, Bonificação) | B3 landing page "eventos"; secondary guides; absent from sample | Ledger importer should route unknown `Tipo de Movimentação` to a review queue, not drop |
| 13 | Costs never present | `Valor = Qtd × Preço` for all 82 rows | Manual/rules-based fees; don't invent |
| 14 | Asset class not present; suffix misleading | `ALUP11` unit, `ROXO34` BDR | External reference table keyed by ticker |
| 15 | Report is "consultive", not tax-grade | B3 flyer disclaimer | Product copy should say so |

**In-account verification checklist (closes the remaining gaps):**
1. Relatório Consolidado: does it offer a **monthly** period? What are the exact Excel **sheet names, columns, and units** for a position line (ticker? CNPJ? quantity? average cost? closing price?)? Does it include CDB/Tesouro Direto positions?
2. Movimentação export: exact columns and the `Tipo de Movimentação` value domain (are Transferência/Grupamento/Desdobramento/Bonificação present?), and the maximum selectable date range (Cota B3 claims 12 months per export — unverified).
3. Is the sample actually from the "Negociação" screen or the "Movimentação" screen's Negociação view? (File name says movimentações; sheet says Negociação.)
4. Proventos: is there a download of the proventos list (dates, type, gross/net, per asset), or only inside the Relatório Consolidado?
5. "Portfolio de investimentos"/"Extratos" screens: any own Excel download?

## 7. Sources

**Primary (B3-owned):**
1. B3 — Área do Investidor product page (datasets, positioning, reports): <https://www.b3.com.br/pt_br/produtos-e-servicos/central-depositaria/canal-com-investidores/area-do-investidor/> — fetched 2026-09-15.
2. B3 — "IRPF 2025: Suas informações na Área do Investidor" (official PDF flyer; Relatório Consolidado path, formats, contents, issuer CNPJs): <https://atendimento.b3.com.br/sys_attachment.do?sys_id=5cd48fcb9724ee905649fe46f053af85> — fetched 2026-09-15.
3. B3 — Atende B3 help-desk article "Preciso do meu extrato … custodiados na B3 … onde posso consultá-lo?" (Relatório Consolidado = position + proventos + loan reimbursements): <https://atendimento.b3.com.br/atendimento?id=kb_article_b3&sys_id=8f3dad2d33d63e1c64f56b07ee5c7bf3> — fetched 2026-09-15 via the portal's page API.
4. B3 — "Manual Técnico APIs – Área do Investidor da B3" (doc dated 12/12/2024; version history up to 10/06/2026): API catalogue (Posição, Movimentação, Negociação de Ativos, Eventos Provisionados…), D-1 freshness, data from 01/11/2019, licensing terms and fees: <https://www.b3.com.br/lumis/portal/file/fileDownload.jsp?fileId=8AE490CA9358B1A70193BADF061C63AF> — downloaded 2026-09-15.
5. B3 — Integrações da Área do Investidor (APIs) product page: <https://www.b3.com.br/pt_br/produtos-e-servicos/central-depositaria/canal-com-investidores/integracoes-da-area-do-investidor-apis/> — fetched 2026-09-15.
6. Bora Investir (B3 editorial) — "Imposto de renda: como usar a Área do Investidor B3 como suporte na declaração", published 27/03/2025, updated 24/06/2025 (classes covered by the consolidated report; proventos history): <https://borainvestir.b3.com.br/noticias/imposto-de-renda/imposto-de-renda-como-usar-a-area-do-investidor-b3-como-suporte-na-declaracao/> — fetched 2026-09-15.
7. Bora Investir — "O que é a Área do Investidor da B3 e como ela ajuda investidores?", published 26/10/2023, updated 06/01/2024 (reports exportable PDF/Excel; "mês a mês ou ano a ano"; position fields `valor aplicado`/`valor líquido`/`preço de fechamento`; proventos tab): <https://borainvestir.b3.com.br/objetivos-financeiros/organizar-as-contas/o-que-e-a-area-do-investidor-da-b3/> — fetched 2026-09-15.

**Secondary (cited as such):**
8. Cota B3 — "Como exportar o extrato de movimentações da B3" (XLSX download steps, claimed 12-month range, movements include splits/grupamentos/transferências, history from 2019): <https://www.cotab3.com.br/artigos/como-exportar-extrato-movimentacoes-b3> — fetched 2026-09-15.
9. Declarante — "Como exportar o extrato da B3 em Excel" (Extratos e Informativos → Movimentação; date range + FILTRAR; BAIXAR → "Arquivo em Excel para ser importado em planilhas"): <https://declarante.com.br/guias/como-exportar-extrato-b3-em-excel> — fetched 2026-09-15.

**In-repo sample:** `references/movimentacoes_B3.xlsx` (git-ignored; parsed 2026-09-15 with openpyxl 3.1.5 + raw OOXML inspection).

## Open uncertainty

- Exact column schema of the **Relatório Consolidado Excel** (the seed source) is not published: quantity/average cost presence is unconfirmed.
- Whether the monthly period actually exists today (2023 article says yes; 2025/2026 B3 materials only say "Anual").
- Exact column schema and `Tipo de Movimentação` domain of the **Movimentação** export; 12-month range limit is secondary-source only.
- Whether a standalone **proventos** export exists.
- Whether the repo sample came from a "Negociação" screen or from a "Movimentação" export view.
