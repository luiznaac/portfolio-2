---
source: luiznaac/portfolio-2#100
branch: research/xp-strategy-reports
date: 2026-09-15
---

# Parsing XP strategy reports — suggested weights, tickers and fields

Research for the XP strategy-report import ticket, 2026-09-15.

**Question:** what is the reliable way to extract the suggested weights and ticker list from each XP
strategy report type (Top Ações, Top Dividendos, Top Dividendos Plus, Top Small Caps, FIIs /
Carteira Fundamentalista)? Cover extraction approach, available fields (ticker, weight, rating,
target price, date, sector), how entries/exits and "não operar" appear, report-to-report drift,
and failure modes. Deliverable: findings + feasibility verdict for automatic parsing with manual
override.

**Samples analysed** (all in `references/xp relatorios/`, git-ignored, read-only):

| File | Report | Pages |
| --- | --- | --- |
| `Top Ações XP &#8211.pdf` | Carteira Top Ações XP | 6 |
| `Carteira Top Dividendos &#8211.pdf` | Carteira Top Dividendos XP | 7 |
| `Carteira Top Dividendos Plus &#8211.pdf` | Carteira Top Dividendos Plus | 9 |
| `Carteira Top Small Caps &#8211.pdf` | Carteira Top Small Caps XP | 6 |
| `Carteira-Fundamentalista-XP-09-2026-3.pdf` | Carteira Fundamentalista (FIIs) | 40 |

All five are PowerPoint-produced PDFs with selectable text. No page required OCR: every table and
field quoted below was read with plain text extraction (pypdf 6.18.0). Every number quoted below
was copied verbatim from the extraction.

## TL;DR

1. **Do not OCR. Do not use pdfplumber's `text` strategy.** Extraction that works, in order of
   usefulness:
   - `pypdf` layout mode (`page.extract_text(extraction_mode="layout")`) — column-aligned output,
     works on **all five** report types, including tables that have no ruling lines (Dividendos
     Plus). This is the recommended primary method.
   - `pdfplumber` with default `lines` strategy — yields real cell matrices for the ruled tables
     (equity page-1 table and FII table). Good as a cross-check/validator, not as the only method.
   - `pypdf` plain text mode — clean one-row-per-asset output for Dividendos Plus, but fragile
     elsewhere (bio text boxes and comments interleave).
   - `pdfplumber` `text` strategy — unusable; columns fragment into dozens of tiny cells
     (verified on all report types).
2. **Every report type is parseable with a page-scoped parser** that (a) targets the known page
   number for the current-edition table, (b) extracts by ticker anchored regex, (c) forward-fills
   merged `Segmento`/`Setor` cells, (d) ignores the previous-month performance tables.
3. **Feasibility: YES with manual override** for all five report types; confidence HIGH for Top
   Ações / Dividendos / Small Caps / Dividendos Plus, HIGH for the FII main table,
   MEDIUM-HIGH for the FII per-fund cards (target price, DY etc.).
4. The safest import flow is: parse → show a staging diff against the last import (entries,
   exits, weight changes) → human confirms. The reports themselves publish the change summary as
   prose, not as structured data, so entry/exit classification should be derived by diffing two
   parsed editions, using the prose only as a cross-check.

## Method tested (reproducible)

Tooling available on this machine: `pypdf 6.18.0`, `pdfplumber 0.11.10`, `openpyxl 3.1.5`,
Python 3.13. `pdftotext`, `camelot`, `pandas` and PyMuPDF are **not** installed; nothing was
installed.

Test scripts (throwaway, under `%TEMP%\opencode`) ran:
`pypdf` `extract_text()` and `extract_text(extraction_mode="layout")`, and
`pdfplumber` `extract_tables()` with `lines` and `text` strategies, on the pages below.
No text-page was found to need a fallback.

## Per report type

### Top Ações XP (`Top Ações XP &#8211.pdf`, 6 pages, edition "Setembro 2026")

- **Portfolio table:** page 1, one table. The header line as printed:
  `Segmento | Setor | Peso do setor (Ibovespa) | Peso do setor (Carteira) | Companhia | Ticker | Peso | Rating | Preço-Alvo`
  (page 1).
- Sample rows verbatim (page 1):
  - `Energia 18,1% 15,0% Petrobras PETR4 10,0% Compra R$ 63,00`
  - `Vale VALE3 5,0% Neutro R$ 85,00`
  - `Cíclicas Domésticas Bens Industriais 8,8% 10,0% Embraer EMBJ3 10,0% Compra R$ 87,00`
  - `Financeiro Financeiro 26,7% 17,5% Itaú Unibanco ITUB4 7,5% Compra R$ 50,00`
- **Detail table:** page 4, header `Companhia | Ticker | Peso | Recomendação | Preço-Alvo | Comentários | Link para tese`,
  rows repeat the same weights and target prices, e.g. `Petrobras PETR4 10,0% Compra R$ 63,00`
  (page 4). Redundant with page 1 → useful as a self-check (both tables agreed in this edition).
- **Edition/date:** header block: `1 de setembro de 2026`, `Setembro 2026` (pages 1–4).
  Data base in footnote: `Dados até 31/08/2026` (page 1).
- **Change summary (prose):** page 1 headline
  `Adicionando CURY3 e AXIA3; aumentando RDOR3; removendo LREN3 e ORVR3; reduzindo ITUB4 e ROXO34`,
  and body text on page 1: `Estamos retirando ORVR3 (de 5%).` … `Estamos zerando nossa posição em LREN3.`
- **Trap:** page 3 `Figura 2: Desempenho de cada ativo` lists the **previous** composition with the
  **previous** weights — it still contains `Lojas Renner LREN3 5.00%` and `Orizon ORVR3 5.00%`, and
  `Itaú Unibanco ITUB4 10.00%` where the current table says `7,5%` (page 3 vs page 1).
- **Extraction that works:** pypdf layout mode (perfectly aligned columns, merged cells blank);
  pdfplumber `lines` also returns the 9-column matrix (header + 14 data rows here) with empty cells
  for continued Segmento/Setor. pypdf plain text is row-per-asset but merged cells break the
  column order.

### Carteira Top Dividendos XP (`Carteira Top Dividendos &#8211.pdf`, 7 pages)

- **Portfolio table:** page 1, identical 9-column header as Top Ações. Sample rows verbatim:
  - `Petrobras PETR4 12,5% Compra R$ 63,00`
  - `Vale VALE3 12,5% Neutro R$ 85,00`
  - `Copasa CSMG3 7,5% Compra R$ 88,32`
  - `Caixa Seguridade CXSE3 5,0% Compra R$ 20,00`
- **Detail table:** page 5, same 7-column header as Top Ações page 4.
- **Change section:** page 2, section title `Mudanças na carteira`, headline
  `Aumentando PETR4 e ALOS3; reduzindo ITUB4` (pages 1–2).
- **Trap:** page 3 `Figura 1: Desempenho de cada ativo` is the previous composition with previous
  weights (`Itaú Unibanco ITUB4 15.00%`, `Petrobras PETR4 10.00%`, page 3 vs `10,0%` / `12,5%` on
  page 1).
- **Extraction that works:** same as Top Ações (pypdf layout mode primary; pdfplumber `lines` as
  matrix cross-check: header + 11 data rows this edition).

### Carteira Top Dividendos Plus (`Carteira Top Dividendos Plus &#8211.pdf`, 9 pages)

- **Portfolio table:** pages 1 and 3. Page 1 header as printed:
  `Companhia (em ordem alfabética) | Ticker | Peso | Setor`; page 3 header adds the DY column:
  `Companhia | Ticker | Peso | Setor | Dividend Yield 2026E ¹` (`¹` footnote on page 3:
  `Os valores de Dividend Yield esperado para o fim de 2026 são estimativas do consenso de mercado`).
- Sample rows verbatim (page 3):
  - `Allos ALOS3 10% Income Properties 13.4%`
  - `Petrobras PETR4 10% Oil, Gas & Petrochemicals 16.1%`
  - `Energisa ENGI11 10% Utilities & Energy 2.9%`
- **No rating and no target price anywhere** in this report. Weight is always `10%` (10 names,
  equal-weighted, page 3).
- **Edition/date:** `Setembro 2026` / `1 de setembro de 2026` (pages 1, 3).
- **Change summary:** `Alterações em relação à última publicação: Entrada de IGTI11 e RENT3, … Saída de VALE3 e ITUB4, …`
  (pages 1 and 3 — same paragraph twice). This is the only report with explicit `Entrada`/`Saída`
  wording.
- **Trap:** page 5 `Figura 5: Desempenho de cada ativo – Carteira Top Dividendos Plus Agosto/2026`
  explicitly shows the **last published** portfolio: it still has `Itaú Unibanco ITUB4` and
  `Vale VALE3` and lacks the two new names (page 5).
- **Extraction that works:** pypdf layout mode (pages 1/3/5, verified aligned); pypdf plain text
  is also clean here because each row is a single text box; pdfplumber `lines` returns only the
  header (body has no ruling lines) — do not rely on it alone.

### Carteira Top Small Caps XP (`Carteira Top Small Caps &#8211.pdf`, 6 pages)

- **Portfolio table:** page 1, same 9-column header as Top Ações/Dividendos. Sample rows verbatim:
  - `Comunicações 1,5% 10,0% Bemobi BMOB3 10,0% Compra R$ 31,00`
  - `SLC Agrícola SLCE3 7,5% Neutro R$ 17,00`
  - `BR Partners BRBI11 5,0% Compra R$ 26,00`
- **Detail table:** page 4, header
  `Companhia | Ticker | Peso | Recomendação | Preço Alvo | Comentários | Link para Tese`
  — note `Preço Alvo` (no hyphen) and `Link para Tese` (capital T), a header drift vs the other
  two equity reports. Sample row: `Bemobi BMOB3 10,0% Compra R$ 31,00` (page 4).
- **Change section:** page 2, `Mudanças na carteira`, headline
  `Adicionando DIRR3; aumentando SLCE3; reduzindo CEAB3 e CURY3` (pages 1–2).
- **Trap:** page 3 `Figura 2: Desempenho de cada ativo` is the previous composition (no DIRR3;
  `C&A Modas CEAB3 10.00%`, `SLC Agrícola SLCE3 5.00%` vs the new `5,0%` / `7,5%` on page 1).
- **Extraction that works:** as Top Ações (layout mode primary; pdfplumber `lines` matrix: header +
  14 data rows).

### FIIs — Carteira Fundamentalista (`Carteira-Fundamentalista-XP-09-2026-3.pdf`, 40 pages)

This is a different template ("XP RESEARCH / Research FIIs", cover `Carteira Fundamentalista`,
`16 ativos`, page 2) and needs its own parser.

- **Main table:** page 2, repeated on page 4 as `Figura 2: Carteira Fundamentalista de Fundos Imobiliários`.
  Header as printed is a two-level header:
  `Peso % | Fundo | Valor de Mercado (VM) | Cota | Valor Patrimonial (VP) | VM/VP | Performance | Yield Anualizado | ¹Risco`
  and beneath it
  `Por Ticker | Segmento | Ticker | Recomendação | Nome | (R$ milhões) | (R$) | (R$/Cota) | % | No mês | Em 12m | DY 12m | (pts.)`
  (page 2).
- Sample rows verbatim (page 2):
  - `9,00% Recebíveis MCCI11 COMPRA Mauá Capital Recebíveis 1.621 96 94 102% 1,9% 25,7% 12,6% 21`
  - `4,50% Lajes Corporat. PVBI11 COMPRA VBI Prime Offices 1.858 69 105 66% -1,2% -4,0% 7,0% 17`
  - `1,50% Híbrido KNRI11 COMPRA Kinea Renda Imobiliária 4.442 158 163 96% 0,5% 21,8% 8,4% 17`
  - footer/aggregate row: `90% 12,4% 19` (page 2). It carries no label. Checked against the 16
    rows: it is the portfolio aggregate — weighted VM/VP 89.8 ≈ 90%, weighted DY 12m 12.37 ≈ 12.4%,
    weighted Risco 19.4 ≈ 19. A parser must recognise and drop (or use as a sanity check) this row.
- **All 16 funds are rated `COMPRA`** in this edition; no other rating token appears in the
  main table (page 2).
- **Per-fund cards (target price and more):** pages 7–37, one fund every two pages (fund header +
  card on odd pages 7, 9, …, 37). Card fields verbatim (page 7, MCCI11):
  - `MCCI11` … `COMPRA`
  - `Preço-Atual (R$/cota) 95,60`
  - `Preço Alvo (R$/cota) 94,00`
  - `Upside (%) -1,7%`
  - `DY Anualizado 12,6%`
  - `Informações Adicionais` → `Gestor JiveMauá`, `Taxa de Gestão (a.a.) 1,0% a.a.`,
    `Patrimônio Líquido (R$ mi) 1.594`, `ADTV (R$ mil) 4.640`
  - All 16 cards follow this exact shape (verified programmatically across pages 7–37: 16/16).
    Target prices range from `R$ 8,56` (CPTS11, page 27) to `R$ 163,89` (KNRI11, page 37). Note the
    card's `Preço-Atual` is rounded to whole reais in the main table's `Cota` column (95,60 on the
    card vs `96` in the table, page 2 vs page 7).
- **Sector/segment weights:** page 4 prose: `Recebíveis (40,25%), FOF/Multiestratégia (17%),
  Ativos Logísticos (20,75%), Shoppings (16,00%), e Lajes Corporativas (4,5%), Híbrido (1,50%)` —
  matches the sum of the row weights per `Segmento` exactly (checked).
- **Edition/date:** `Setembro 2026`, `1 de setembro de 2026`, `Data base: 31/08/2026` (pages 1–2).
- **Change summary:** page 2 `Alterações: Para setembro, reduzimos a alocação em CPTS11 (-1,0 p.p.),
  PVBI11 (-0,75 p.p.) e LVBI11 (-0,75 p.p.). Em contrapartida, ampliamos a exposição a XPLG11
  (+1,25 p.p.), HGBS11 (+1,0 p.p.) e XPCI11 (+0,25p.p.).`; page 4 repeats it as bullets
  `Alterações para Setembro:` with `Reduzimos…` / `Ampliamos…`.
- **Extraction that works:** pypdf layout mode yields the aligned 13-column table (including the
  hierarchical header, page 2) and the `Key value` card lines (page 7 and following). pdfplumber
  `lines` also returns the main table but splits the aggregate row into separate fragments
  (`90%` in the VM/VP column, then a stray table with `12,4% | 19`) — a reminder the footer row is
  easy to mis-join. pypdf layout mode emits a `Rotated text discovered. Output will be incomplete.`
  warning on card pages (chart axis labels); in the sample the card fields were still complete, but
  treat that warning as a signal to check completeness.

## Field-by-field mapping

| Field | Top Ações | Top Dividendos | Dividendos Plus | Small Caps | FII / Fundamentalista |
| --- | --- | --- | --- | --- | --- |
| Ticker | `Ticker`, p1/p4 | `Ticker`, p1/p5 | `Ticker`, p1/p3 | `Ticker`, p1/p4 | `Ticker`, p2/p4; card header p7+ |
| Company name | `Companhia` | `Companhia` | `Companhia` | `Companhia` | `Nome` |
| Weight | `Peso` (e.g. `10,0%`), p1/p4 | `Peso`, p1/p5 | `Peso` always `10%`, p1/p3 | `Peso`, p1/p4 | `Peso %` (e.g. `9,00%`), p2/p4 |
| Rating | `Rating` p1 / `Recomendação` p4; observed `Compra`, `Neutro` | same | **absent** | same | `Recomendação`; observed only `COMPRA` (uppercase) |
| Target price | `Preço-Alvo` p1/p4 (`R$ 63,00`) | `Preço-Alvo` p1/p5 | **absent** | `Preço-Alvo` p1 / `Preço Alvo` p4 | **not in main table**; card `Preço Alvo (R$/cota)` (`94,00`) p7+ |
| Current price | — | — | — | — | `Cota` p2/p4; card `Preço-Atual (R$/cota)` p7+ |
| Sector | `Segmento` + `Setor` p1 (pt-BR taxonomy, e.g. `Commodities`/`Energia`) | same | `Setor` (English taxonomy, e.g. `Income Properties`, `Banks`) | same | `Segmento` (FII taxonomy, e.g. `Recebíveis`, `Ativos Logísticos`, `Lajes Corporat.`, `Shoppings`, `FOF/Multiestratégia`, `Híbrido`) |
| Sector weights | `Peso do setor (Ibovespa)` and `Peso do setor (Carteira)` p1 | same | segment totals in prose p4 | same | segment totals in prose p4 |
| Edition / date | `Setembro 2026`; `1 de setembro de 2026` | same | same | same | `Setembro 2026`; `1 de setembro de 2026`; `Data base: 31/08/2026` |
| Dividend yield | — | — | `Dividend Yield 2026E ¹` p3 (estimate, consenso) | — | `Yield Anualizado DY 12m` p2/p4; card `DY Anualizado` p7+ |
| Other per-row fields | — | — | `Data de entrada` + returns (previous portfolio table p5 only) | — | `VM (R$ milhões)`, `VP (R$/Cota)`, `VM/VP %`, `No mês`, `Em 12m`, `¹Risco (pts.)` p2/p4; card `Upside`, `Gestor`, `Taxa de Gestão`, `Patrimônio Líquido`, `ADTV` p7+ |

Weights in the current-edition tables sum to 100% in this edition for all five reports (checked:
Top Ações 100.0, Dividendos 100.0, Small Caps 100.0, Dividendos Plus 10×10 = 100, FII 100.0) and
each report's sector/segment totals equal the sum of their constituents.

## Entries, exits and "não operar"

- **"Não operar" does not appear anywhere in the five samples.** Case-insensitive search for
  `operar` over all extracted text returned zero hits (the only near-words are `operação`/`operações`
  in prose). There is no "hold"/"do not trade" column in any table.
- Entry/exit is expressed three ways, none of them structured:
  1. **Headline prose** (Top Ações, Dividendos, Small Caps): `Adicionando CURY3 e AXIA3; aumentando
     RDOR3; removendo LREN3 e ORVR3; reduzindo ITUB4 e ROXO34` (Top Ações p1) and a `Mudanças na
     carteira` section on p2 of Dividendos/Small Caps. Verbs observed: `Adicionando`, `removendo`,
     `aumentando`, `reduzindo`, `retirando`, `zerando` (Top Ações body p1).
  2. **Explicit Entrada/Saída prose** (Dividendos Plus only): `Entrada de IGTI11 e RENT3 … Saída de
     VALE3 e ITUB4` (p1 and p3).
  3. **Composition diff between editions**: a removed ticker simply disappears from the current
     table and appears only in the previous-month performance table (LREN3/ORVR3 on Top Ações p3;
     ITUB4/VALE3 on Dividend Plus p5). A parser should diff the parsed current edition against the
     previous stored import to classify ADD/REMOVE/INCREASE/DECREASE, and treat the prose as a
     cross-check label only.
- The FII report has no entry/exit table either; changes are weight deltas in basis points in
  prose (`reduzimos … (-1,0 p.p.)`, `ampliamos … (+1,25 p.p.)`, page 2/4). No FII was added or
  removed in this edition: the August performance page (page 5) lists the same 16 tickers as the
  September table, and the prose contains no `Entrada`/`Saída`. An entry/exit would therefore have
  to be detected by diffing the ticker sets of two parsed editions.

## Report-to-report drift and failure modes

### Structural drift between report families

- **Three templates, not one:** Top/Dividendos/Small Caps share the 9-column `Segmento/Setor/Peso
  do setor…/Companhia/Ticker/Peso/Rating/Preço-Alvo` table; Dividend Plus is a quant sheet with
  4–5 columns, no rating, no target price, English sectors and equal weights; the FII report is a
  40-page template with a 13-column table and 16 two-page fund cards.
- **Header drift inside the equity family:** `Preço-Alvo` (Top Ações p1/p4, Dividendos p1/p5) vs
  `Preço Alvo` and `Link para Tese` (Small Caps p4). Match case-insensitively and tolerate the
  hyphen.
- **Taxonomy drift:** pt-BR `Segmento`/`Setor` (equities), English GICS-like `Setor` (Dividend
  Plus), FII `Segmento`. A normalized `sector` field must be mapped per report type, not globally.
- **Decimal separator and precision drift:** current tables use comma decimals (`10,0%`, `9,00%`,
  `R$ 63,00`) while the previous-month performance tables use dot decimals (`10.00%`, `-0.4%`);
  Dividend Plus uses integers (`10%`) and DY with dot (`13.4%`). Parse per table, never with one
  global locale assumption.
- **Date drift:** cover dates are long-form pt-BR (`1 de setembro de 2026`); previous-month tables
  use `mmm-yy` (`dez-25`, Top Ações p3) or `dd/mm/yyyy` (`01/04/2026`, Dividend Plus p5); the FII
  report adds `Data base: 31/08/2026`.
- **Main-table duplication:** the FII main table appears twice (p2 and p4) and Dividend Plus's
  appears twice (p1 and p3, with and without DY). Deduplicate by `(report, edition, ticker)`, or
  pick the richer page.

### Month-to-month drift (not directly measurable here)

Only one edition per report type was supplied (all September 2026). What the samples *do* show is
that composition changes month to month (prose `Mudanças na carteira` / `Alterações`, and the
previous-month tables differ from the current ones). Whether the slide layout itself changes
between months is **unverified** — plan for a parser that fails loudly (missing header, wrong row
count, weight sum ≠ 100) instead of silently importing, and keep the previous edition's raw text
for diffing.

### Concrete failure modes for a parser (all observed)

1. **Previous-month table shadowing the current one** — Top Ações p3, Dividendos p3, Small Caps p3
   and Dividend Plus p5 contain tickers that were removed and weights that are stale. Any
   "scan the whole PDF for tickers" approach imports wrong weights. Page-scope the parser.
2. **Merged cells** — in the equity tables `Segmento`/`Setor`/`Peso do setor…` are printed only on
   the first row of the group and blank afterwards (Top Ações p1 layout output shows the blanks).
   Forward-fill required; pdfplumber's matrix marks them `None`.
3. **Aggregate footer row** — FII table ends with the unlabeled `90% 12,4% 19` (p2/p4). It looks
   like a data row to a naive line parser (but has no ticker) and gets split across tables by
   pdfplumber.
4. **Multiple candidate numbers per row** — e.g. FII row has weight, VM, price, book value, VM/VP,
   two performances, DY, risk; equity rows have 3 percentages (two sector weights + asset weight).
   Anchor on ticker + expected column sequence, and validate `weight ≤ 100`.
5. **Comments glued to detail rows** — on the detail pages (Top Ações p4 etc.) the long comment can
   be on the same extracted line as the data (`TOTVS TOTS3 5,0% Compra R$ 50,50 Alta em agosto…`).
   Regex must capture the left-anchored prefix, not "rest of line".
6. **Ratings beyond the sample** — only `Compra`/`Neutro` (equity) and `COMPRA` (FII) occur in
   these editions; `Venda`/`VENDA` must still be accepted by the parser (case-insensitive), and a
   `Neutro` asset still carries a `Peso` and a `Preço-Alvo` (e.g. VALE3 Top Ações p1).
7. **Unlabeled totals row in prose** — sector totals in the FII text (`Recebíveis (40,25%)`) and
   equity `Peso do setor (Carteira)` are not per-asset weights; don't double count if both are
   parsed into the same field.
8. **Rotated-text warning** — pypdf layout mode logs `Rotated text discovered. Output will be
   incomplete.` on the FII card pages; the card fields survived in the sample but the parser should
   treat the warning (or a missing `Preço Alvo` for a known ticker) as a parse failure requiring
   manual review.
9. **Price rounding mismatch** — FII table `Cota` (e.g. `96`) vs card `Preço-Atual` (`95,60`);
   choose the card as the precise source or store both.
10. **File naming** — the FII file (`Carteira-Fundamentalista-XP-09-2026-3.pdf`) doesn't match its
    cover title (`Carteira Fundamentalista`), and equity filenames contain literal HTML entities
    (`&#8211;`). Don't rely on file names for report-type detection; detect from the cover text
    (`Carteira Top Ações XP`, `Carteira Top Dividendos XP`, `Carteira Top Dividendos Plus`,
    `Carteira Top Small Caps XP`, `Carteira Fundamentalista` + `Research FIIs`).

## Feasibility verdict

| Report type | Extracted fields | Verdict | Confidence |
| --- | --- | --- | --- |
| Top Ações XP | ticker, name, weight, rating, target price, segmento/setor, sector weights, date | Automatic parse + manual override; page 1 (fallback page 4) | **High** |
| Top Dividendos XP | same as Top Ações | Automatic parse + manual override; page 1 (fallback page 5) | **High** |
| Top Dividendos Plus | ticker, name, weight (10%), setor, DY 2026E, Entrada/Saída prose | Automatic parse + manual override; page 3 (DY) or 1, page 5 for previous-portfolio perf | **High** |
| Top Small Caps XP | same as Top Ações | Automatic parse + manual override; page 1 (fallback page 4) | **High** |
| FIIs / Fundamentalista (main table) | ticker, name, weight, segmento, recommendation, VM, cota, VP, VM/VP, perf month/12m, DY 12m, risk pts | Automatic parse + manual override; page 2 (p4 duplicate) | **High** |
| FIIs / Fundamentalista (per-fund cards) | target price, current price, upside, DY, gestor, fee, PL, ADTV | Automatic parse + manual override; pages 7–37 odd | **Medium-High** (rotated-text warning; positional mapping) |

No sample report was unusable; OCR is unnecessary. The residual risk is not "can we extract" but
"did we extract the current edition and the right table", which the manual-override step covers.

## Recommended normalized output shape

One row per ticker per edition; only `ticker`, `weight_pct` and `edition` are guaranteed across
all five report types. Grounded in what was actually extracted:

```jsonc
{
  "report_type": "TOP_ACOES | TOP_DIVIDENDOS | TOP_DIVIDENDOS_PLUS | TOP_SMALL_CAPS | FII_FUNDAMENTALISTA",
  "edition_label": "Setembro 2026",          // cover text
  "published_on": "2026-09-01",              // long-form cover date, normalized
  "data_base_on": "2026-08-31",              // FII only; null elsewhere
  "source_file": "Carteira Top Dividendos &#8211.pdf",
  "source_page": 1,                          // page the row came from
  "parse_confidence": "high",                // per-row, drives the override flag

  "rows": [
    {
      "ticker": "PETR4",
      "company_name": "Petrobras",
      "weight_pct": 12.5,                    // current edition only
      "rating": "Compra",                    // Compra|Neutro|Venda (case-normalized); null in Dividend Plus
      "target_price": 63.00,                 // equity tables + FII cards; null in Dividend Plus / FII main table
      "sector": "Energia",                   // raw label as printed
      "segment": "Commodities",              // equity reports only; null otherwise
      "sector_weight_ibovespa_pct": 18.1,    // equity page-1 table only
      "sector_weight_portfolio_pct": 17.5,   // equity page-1 table only

      // FII main table / cards only:
      "recommendation": null,                // FII "COMPRA"
      "current_price": null,                 // FII Cota (table) or Preço-Atual (card)
      "book_value_per_share": null,          // FII VP
      "vm_vp_pct": null,                     // FII VM/VP
      "perf_month_pct": null,                // FII "No mês"
      "perf_12m_pct": null,                  // FII "Em 12m"
      "dividend_yield_12m_pct": null,        // FII "DY 12m" / card "DY Anualizado"
      "dividend_yield_2026e_pct": null,      // Dividend Plus only
      "risk_pts": null,                      // FII "¹Risco"
      "market_value_mm": null,               // FII "VM (R$ milhões)"
      "upside_pct": null,                    // FII card
      "manager": null,                       // FII card "Gestor"
      "management_fee_pct": null,            // FII card
      "net_equity_mm": null,                 // FII card
      "adtv_thousand": null,                 // FII card "ADTV (R$ mil)"
      "entry_date": null                     // Dividend Plus previous-portfolio table "Data de entrada"
    }
  ],

  // Derived by diffing this parse against the previous stored edition;
  // the prose headline is kept for audit only.
  "changes": [
    {
      "ticker": "LREN3",
      "action": "REMOVE",                    // ADD | REMOVE | INCREASE | DECREASE | HOLD | UNKNOWN
      "prev_weight_pct": 5.0,
      "new_weight_pct": null,
      "note": "Estamos zerando nossa posição em LREN3."   // verbatim excerpt
    }
  ],

  "warnings": []                             // e.g. "weight sum 98.5 != 100", "rotated text"
}
```

Design notes grounded in the findings:

- Keep `weight_pct` and `rating`/`target_price` independently nullable: Dividend Plus has no
  rating/target; FII main table has no target (only the card does).
- Store the raw sector labels and map to the app's taxonomy later — the three templates use three
  taxonomies.
- `changes` is a **derived** artifact (diff of two imports), not something to mine from prose
  alone; use the prose (`Mudanças na carteira`, `Entrada/Saída`, `Alterações`) as the audit note.
- Validate on every parse: weight sum ≈ 100 per report, row count (14/11/10/14/16 in this edition),
  duplicate ticker detection (FII main table is duplicated on pages 2 and 4) and the aggregate
  footer row on the FII table.

## Open uncertainties

- **Month-to-month layout drift is untested** — only one edition per report type was available.
  The templates are PowerPoint exports whose layout has been stable within the edition, but that is
  an assumption, not a measured fact.
- The FII report's `90% 12,4% 19` footer row has no printed label; its interpretation (portfolio
  weighted average VM/VP, DY 12m, risk pts) is an inference from arithmetic against the 16 rows,
  not from the document text.
- Whether the per-fund card pages are always odd-numbered 7–37 (two pages per fund, 16 funds) is
  observed for this edition only; anchor the mapping on the ticker header (`TICKER COMPRA`)
  rather than page arithmetic.
- No sample contained a `Venda`/`VENDA` rating, a FII addition/removal, or a non-COMPRA FII;
  the change-diff path for those cases is designed but not exercised.
- Report licensing/terms for automated ingestion were not reviewed (personal use only).

## Sources

- `references/xp relatorios/Top Ações XP &#8211.pdf` — page 1 (portfolio table, headline, date,
  footnote), page 3 (previous-composition performance table), page 4 (detail table), pages 5–6
  (disclaimer).
- `references/xp relatorios/Carteira Top Dividendos &#8211.pdf` — page 1 (portfolio table,
  headline), page 2 (`Mudanças na carteira`), page 3 (previous weights), page 5 (detail table).
- `references/xp relatorios/Carteira Top Dividendos Plus &#8211.pdf` — page 1 (table + Entrada/Saída
  prose), page 3 (table with `Dividend Yield 2026E ¹`), page 5 (previous-portfolio performance,
  `Data de entrada`).
- `references/xp relatorios/Carteira Top Small Caps &#8211.pdf` — page 1 (portfolio table,
  headline), page 2 (`Mudanças na carteira`), page 3 (previous weights), page 4 (detail table with
  `Preço Alvo`).
- `references/xp relatorios/Carteira-Fundamentalista-XP-09-2026-3.pdf` — page 1 (cover), page 2
  (main table, `Alterações`, `Data base: 31/08/2026`), page 4 (table duplicate, segment
  diversification, `Alterações para Setembro`), page 5 (performance), pages 7–37 (per-fund cards,
  one per odd page), page 39 (disclaimer).
- Extraction tooling: pypdf 6.18.0 (`extract_text()`, `extract_text(extraction_mode="layout")`)
  and pdfplumber 0.11.10 (`extract_tables()` with `lines` and `text` strategies), run locally on
  2026-09-15; no external sources were needed for the parsing claims.
