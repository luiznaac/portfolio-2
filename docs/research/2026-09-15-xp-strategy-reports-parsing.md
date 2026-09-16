---
source: luiznaac/portfolio-2#93
branch: research/xp-reports
date: 2026-09-15
---

# XP strategy reports — automatic parsing feasibility

Research for GitHub issue #93 ("Research: parse XP strategy reports"). Every claim below is
grounded in the five real PDFs under `references/xp relatorios/` (git-ignored via `/references` in
`.gitignore`), cited as **file → page**. Extraction probes were run with `pypdf` 6.18.0 (Python
3.13.5), `pdfplumber` 0.11.10 (installed user-level, not into the repo) and Apache PDFBox 3.0.6
(the version already pinned in `backend/http-api/build.gradle.kts`), using the JDK at
`C:\Users\rafa\.jdks\ms-21.0.8`. All probe scripts live in
`C:\Users\rafa\AppData\Local\Temp\opencode\xp-research\` (outside the repo).

**Verdict up front:** automatic parsing is feasible with high confidence for ticker + weight on all
five report families, using **plain text-layer extraction + one small anchored parser per report
template + hard validations + a mandatory human confirm/edit step**. No OCR is needed. Table-grid
extraction (`pdfplumber`-style) is *not* the right tool here: it is clean on some tables and broken
on others, while line-based text extraction is clean on all of them. The parser must never trust
printed totals or prose, and must refuse to guess when a table does not match its template.

---

## 1. Samples and method

| # | File (literal name on disk) | Strategy | Pages | Publication date |
|---|---|---|---|---|
| 1 | `Top Ações XP &#8211.pdf` | Carteira Top Ações | 6 | 1 de setembro de 2026 |
| 2 | `Carteira Top Dividendos &#8211.pdf` | Carteira Top Dividendos | 7 | 1 de setembro de 2026 |
| 3 | `Carteira Top Dividendos Plus &#8211.pdf` | Carteira Top Dividendos Plus | 9 | 1 de setembro de 2026 |
| 4 | `Carteira Top Small Caps &#8211.pdf` | Carteira Top Small Caps | 6 | 1 de setembro de 2026 |
| 5 | `Carteira-Fundamentalista-XP-09-2026-3.pdf` | Carteira Fundamentalista de Fundos Imobiliários (FIIs) | 40 | 1 de setembro de 2026 |

Notes:

- The filenames literally contain the string `&#8211` (an HTML-entity artifact from download), not
  an en dash; one name has accented characters (`Top Ações`). Test fixtures and any directory
  scanner must not assume ASCII filenames (verified with `os.listdir` + `repr`, see Appendix A).
- Each report's footer carries the data base, e.g. `Carteira Top Ações XP` p.1: `Dados até
  31/08/2026`; FII p.2: `Data base: 31/08/2026`. The publication date and the reference-date are
  different fields (`1 de setembro de 2026` vs `31/08/2026`).

Tools and commands (all outputs kept in the temp folder):

```powershell
py -3 -m pip install pdfplumber                      # 0.11.10, user-level
py -3 dump_text.py                                    # pypdf: plain + layout text, per page
py -3 dump_tables.py                                  # pdfplumber: find_tables/extract, per page
py -3 diff_words.py                                   # word-set diff pypdf vs pdfplumber, per page
py -3 raw_search.py                                   # raw content-stream search for "operar"
py -3 prototype_parser.py                             # regex prototype over layout text
java -cp "<pdfbox-3.0.6 + fontbox + pdfbox-io + commons-logging jars>;." ExtractXP   # PDFBox 3.0.6 text, default + sortByPosition
```

`pypdf` emits `Rotated text discovered. Output will be incomplete.` in **layout mode** on
chart-heavy pages only (Top Dividendos p.3, Dividendos Plus p.4, FII pages 5/7/9/…/37 — 26 pages);
it never fires on any page that holds a portfolio table (Appendix A). `pdfplumber` reports zero
non-upright characters on all pages of all five files, i.e. the warning is benign for this data.

---

## 2. Findings per report type

### 2.1 Top Ações (`Top Ações XP &#8211.pdf`, 6 pages)

**Where the portfolio lives.** Page 1, lower half, a fully ruled table (15×9 grid, detected by
`pdfplumber` as a real table). Header, verbatim:

> `Segmento  Setor  Peso do setor (Ibovespa)  Peso do setor (Carteira)  Companhia  Ticker  Peso  Rating  Preço-Alvo`

14 holdings. Field map: ticker, weight (`10,0%`), rating (`Compra`/`Neutro`), target price
(`R$ 63,00`), sector (pt-BR, `Energia`, `Materiais`, …), plus the index/carteira sector weights.

**Detail table (p.4)** repeats the same 14 holdings with column set
`Companhia Ticker Peso Recomendação Preço-Alvo Comentários Link para tese` — note `Recomendação`
here vs `Rating` on p.1, and the multi-line free-text `Comentários` column (each comment spans 2–3
visual lines and interleaves with the ticker row in raw text).

**Changes (p.1/p.2), verbatim.** Headline: `Adicionando CURY3 e AXIA3; aumentando RDOR3; removendo
LREN3 e ORVR3; reduzindo ITUB4 e ROXO34`. Prose: `Estamos adicionando AXIA3 com 7,5% … Estamos
adicionando CURY3 com 5% … Estamos aumentando nossa exposição a RDOR3 de 7,5% para 10% … Estamos
reduzindo o peso de ITUB4 de 10,0% para 7,5% e ROXO34 de 7,5% para 5,0% … Estamos retirando ORVR3
(de 5%) … Estamos zerando nossa posição em LREN3.`

**Trap — p.3 is last month, not this month.** The per-asset performance table on p.3 still lists
the *previous* portfolio with old weights: `Itaú Unibanco ITUB4 10.00%`, `Lojas Renner LREN3
5.00%`, `Orizon ORVR3 5.00%`, `Rede D'Or RDOR3 7.50%`, `Nubank ROXO34 7.50%`, and no AXIA3/CURY3.
A parser that scans all pages for ticker+weight will silently mix August and September. Anchoring
on page 1's header (`Companhia Ticker Peso Rating Preço-Alvo` preceded by the `Segmento …` header)
separates them.

**Number/date formats.** p.1 uses comma decimals (`10,0%`); p.3 uses dot decimals (`10.00%`) and
month abbreviations (`dez-25`).

### 2.2 Top Dividendos (`Carteira Top Dividendos &#8211.pdf`, 7 pages)

- **Portfolio table:** page 1, same 9-column template as Top Ações (12×9 grid). 11 holdings.
- **Detail table:** page 5, same columns as Top Ações p.4 (`Recomendação`, `Preço-Alvo`,
  `Comentários`).
- **Changes (p.1/p.2):** headline `Aumentando PETR4 e ALOS3; reduzindo ITUB4`; prose `Estamos
  aumentando o peso de PETR4 de 10% para 12,5% … Estamos aumentando ALOS3 de 7,5% para 10% …
  Estamos reduzindo o peso de ITUB4 de 15,0% para 10,0%`.
- **Trap:** p.3's per-asset table is again the previous portfolio (`ITUB4 15.00%`, `PETR4 10.00%`,
  `ALOS3 7.50%` — all the *old* weights).
- **Drift inside the report:** p.3's last column is `Desempenho 2026` (Top Ações says
  `Desempenho YTD`). p.4 is a dividend-flow table (`Dividendos R$ 686.45 …`), not an allocation
  table — a distractor that a naive "all rows with a `%`" scan could hit.
- **Missing fields:** no `Data de entrada` on p.1 (present on p.3); no per-row sector on the p.5
  detail table.

### 2.3 Top Small Caps (`Carteira Top Small Caps &#8211.pdf`, 6 pages)

- **Portfolio table:** page 1, same 9-column template (15×9 grid). 14 holdings, including *11
  codes that are not FIIs (`IGTI11`, `ALUP11`, `BRBI11`).
- **Detail table:** page 4, header split across two lines `Companhia Ticker Peso Recomendação
  Preço Alvo Comentários Link para Tese` — `Preço Alvo` has **no hyphen** here (vs `Preço-Alvo`
  everywhere else), and `Tese` is capitalised.
- **Changes (p.1/p.2):** headline `Adicionando DIRR3; aumentando SLCE3; reduzindo CEAB3 e CURY3`;
  prose `Estamos adicionando DIRR3 com 5% de peso … Estamos aumentando SLCE3 de 5% para 7,5% …
  Estamos reduzindo nossa posição em CEAB3 de 10% para 5% … Estamos reduzindo CURY3 de 12,5% para
  10%`.
- **Trap:** p.3's per-asset table is the previous portfolio (`CEAB3 10.00%`, `CURY3 12.50%`, no
  DIRR3).

### 2.4 Top Dividendos Plus (`Carteira Top Dividendos Plus &#8211.pdf`, 9 pages) — different template

- Header block is `XP RESEARCH` / `Estratégia Quantitativa`, not `EQUITY RESEARCH` /
  `Estratégia | Carteira XP`. Author: `Antonio Mello, Estrategista Quantitativo`.
- **Portfolio table:** page 1, 10 assets, *equal weight*: header `Companhia (em ordem alfabética)
  Ticker Peso Setor`; rows such as `Allos ALOS3 10% Income Properties`. Sectors are **English**
  (`Banks`, `Homebuilders`, `Utilities & Energy`). Page 3 repeats it with a fifth column
  `Dividend Yield 2026E ¹` (footnote marker attached to the header, footnote text at the bottom of
  p.3).
- **Changes (p.1/p.3), verbatim:** `Alterações em relação à última publicação: Entrada de IGTI11 e
  RENT3, devido ao Dividend Yield atrativo e melhora nas expectativas de analistas, de acordo com
  dados da LSEG. Saída de VALE3 e ITUB4, devido à redução no Dividend Yield esperado e piora nas
  expectativas de analistas, segundo dados da LSEG, bem como para equilíbrio da estratégia.`
  (Entries/exits only — the prose never states the new weights, because all weights are 10%.)
- **Trap:** page 5's per-asset table is the *previous* (August) portfolio — it still contains
  `Itaú Unibanco ITUB4 10%` and `Vale VALE3 10%` and lacks IGTI11/RENT3; its title says
  `Em 31/08/2026` and the figure caption says `Agosto/2026`. Its `Data de entrada` column uses
  `dd/MM/yyyy` (`01/04/2026`), unlike the Top reports' `mmm-yy`.
- No rating, no target price, no `Peso do setor` columns anywhere in this template.

### 2.5 Carteira Fundamentalista de FIIs (`Carteira-Fundamentalista-XP-09-2026-3.pdf`, 40 pages)

Structure map (page numbers verified):

| Pages | Content |
|---|---|
| 1 | Cover |
| 2 | Summary + **portfolio table** (16 funds + 1 total row; pdfplumber's grid detection breaks it into 5×15) |
| 3 | Index |
| 4 | `Figura 2: Carteira Fundamentalista de Fundos Imobiliários` — **same table again** + sector diversification pie |
| 5 | Performance table (dots decimals, `set-25…ago-26`) + a bar chart whose labels are the 16 tickers |
| 6 | Sector views |
| 7–38 | 16 fund deep-dives, 2 pages each (odd page: thesis text + price block; even page: charts) |
| 39 | Disclaimer |
| 40 | Black back cover (full-page image, no meaningful text) |

- **Table header (pages 2 and 4), verbatim, two visual lines:**
  `Peso % | Fundo | Valor de Mercado (VM) | Cota | Valor Patrimonial (VP) | VM/VP | Performance |
  Yield Anualizado | ¹Risco` over `Por Ticker | Segmento | Ticker | Recomendação | Nome | (R$
  milhões) | (R$) | (R$/Cota) | % | No mês | Em 12m | DY 12m | (pts.)`.
  16 rows, e.g. `9,00% Recebíveis MCCI11 COMPRA Mauá Capital Recebíveis 1.621 96 94 102% 1,9%
  25,7% 12,6% 21`.
- **Fields:** ticker, weight (`9,00%`), recommendation (always `COMPRA` in this edition), segment
  (`Recebíveis`, `Ativos Logísticos`, `Lajes Corporat.`, `Shoppings`, `FOF/Multiestratégia`,
  `Híbrido`), fund name, market value, quota price, book value, P/VP, monthly/12-month performance,
  12-month DY, risk score. **No target price in the table and no entry date.**
- **Target price lives only on the deep-dive pages**, one block per fund, e.g. p.7:
  `MCCI11 COMPRA` / `Preço-Atual (R$/cota) 95,60` / `Preço Alvo (R$/cota) 94,00` / `Upside (%)
  -1,7%` / `DY Anualizado 12,6%`. All 16 funds have this block, on pages 7, 9, 11, …, 37.
- **Changes (p.2 and p.4), in percentage points, not new weights:** `Alterações: Para setembro,
  reduzimos a alocação em CPTS11 (-1,0 p.p.), PVBI11 (-0,75 p.p.) e LVBI11 (-0,75 p.p.). Em
  contrapartida, ampliamos a exposição a XPLG11 (+1,25 p.p.), HGBS11 (+1,0 p.p.) e XPCI11
  (+0,25p.p.).` p.4 repeats it as bullets. Deltas are consistent with the table (e.g. CPTS11
  9,00% → 8,00%).
- **Total-row anomaly:** the table prints a total row `90%  12,4%  19` (verified in the raw
  character stream, not an extraction artifact), while the 16 weights sum to exactly `100.00%`.
  The printed total **must not be trusted**; a parser should sum the rows itself.
- **Trap:** the prose pages mention 30 distinct tickers (portfolio 16 + comparison funds such as
  `HGLG11`, `RBRL11`, `SOPP11`, `VCJR11`, `RPRI11`); performance charts on p.5 also carry the 16
  tickers as image labels.

---

## 3. Cross-report drift (observed in this single edition of each)

| Aspect | Top Ações | Top Dividendos | Top Small Caps | Top Dividendos Plus | FII Fundamentalista |
|---|---|---|---|---|---|
| Template block | `EQUITY RESEARCH` | same | same | `XP RESEARCH` / `Estratégia Quantitativa` | `XP RESEARCH` / `Research FIIs` |
| Portfolio table page | 1 | 1 | 1 | 1 (repeated on 3 with DY) | 2 and 4 (duplicated) |
| Detail table page | 4 | 5 | 4 | — | per-fund pages 7–38 |
| Weight format | `10,0%` | `10,0%` | `10,0%` | `10%` (integer) | `9,00%` |
| Rating column | `Rating` | `Rating` | `Rating` | absent | `Recomendação` (`COMPRA`) |
| Target column | `Preço-Alvo` | `Preço-Alvo` | `Preço Alvo` (detail) | absent | per-fund `Preço Alvo (R$/cota)` |
| Sector language | pt-BR | pt-BR | pt-BR | en-US | pt-BR segment |
| Performance table last col | `Desempenho YTD` | `Desempenho 2026` | `Desempenho 2026` | `Em 2026` | n/a (fundamentalist report) |
| Change wording | `Adicionando/…/removendo/…/reduzindo` | `Aumentando …; reduzindo …` | `Adicionando …; aumentando …; reduzindo …` | `Entrada de … / Saída de …` | `reduzimos … (-x p.p.) / ampliamos … (+y p.p.)` |
| Entry dates | `dez-25` | `dez-25` | `dez-25` | `01/04/2026` | absent |

Ticker formatting: PT-BR B3 conventions — 4 alphanumeric + class digits: `ROXO34` (BDR),
`IGTI11`/`ALUP11`/`BRBI11`/all FIIs (`*11`), and the regex trap **`B3SA3`** (a digit inside the
first four characters). A naive `[A-Z]{4}\d{1,2}` misses `B3SA3`; `[A-Z][A-Z0-9]{3}\d{1,2}` covers
every ticker in the five samples (verified — the first prototype missed B3SA3 and produced 10 rows
/ 90% for Top Dividendos). Fractional tickers (`ALUP11F`) do not appear here but are already
normalised elsewhere in the backend (`backend/AGENTS.md`).

Number formats: comma decimals on portfolio tables (`10,0%`), dot decimals on performance tables
(`10.00%`, `20.05%`) and in the FII performance table (`0.5%`); thousands with dot in FII (`1.621`)
and with comma in the dividend flow (`R$ 686.45` — mixed). Dates: `1 de setembro de 2026`,
`31/08/2026`, `dez-25`, `01/04/2026`.

---

## 4. Entries/exits and "não operar"

Observed mechanisms, all prose (no dedicated signals table):

| Report | Entry wording | Exit wording |
|---|---|---|
| Top Ações | `Adicionando CURY3 e AXIA3`, `Estamos adicionando AXIA3 com 7,5%`, `Estamos aumentando nossa exposição a RDOR3 de 7,5% para 10%` | `removendo LREN3 e ORVR3`, `Estamos retirando ORVR3 (de 5%)`, `Estamos zerando nossa posição em LREN3` |
| Top Dividendos | `Aumentando PETR4 e ALOS3`, `Estamos aumentando o peso de PETR4 de 10% para 12,5%` | `reduzindo ITUB4`, `Estamos reduzindo o peso de ITUB4 de 15,0% para 10,0%` |
| Top Small Caps | `Adicionando DIRR3`, `Estamos adicionando DIRR3 com 5% de peso`, `Estamos aumentando SLCE3 de 5% para 7,5%` | `reduzindo CEAB3 e CURY3`, `Estamos reduzindo nossa posição em CEAB3 de 10% para 5%` |
| Dividendos Plus | `Entrada de IGTI11 e RENT3` | `Saída de VALE3 e ITUB4` |
| FII | `ampliamos a exposição a XPLG11 (+1,25 p.p.)` | `reduzimos a alocação em CPTS11 (-1,0 p.p.)` |

Important for design: a weight change for an existing name is expressed as `de X para Y` in the
three Top reports and as a `p.p.` delta only in the FII report; Dividendos Plus only names
entries/exits. **Prose is never the authoritative source of the new weight** — the portfolio table
is; prose is at best a diff hint to show the user.

**"Não operar" does not appear anywhere in the five samples.** Verified two ways: (a) literal
search for `operar` over the full extracted text of all five PDFs (plain and layout modes) returns
zero matches; (b) raw PDF content-stream search over every page returns zero matches. All rating
values present are `Compra` or `Neutro` (Top reports) / `COMPRA` (FII). If "não operar" belongs to
XP's report taxonomy, it is not part of these five monthly model-portfolio reports (it may exist in
XP's weekly/trading-oriented letters, which are out of this sample). The parser should treat any
future `Não operar` rating as a normal unsupported value for the affected row and flag it for
manual review rather than guess.

---

## 5. Extraction approach — what works, what doesn't

### 5.1 pypdf plain text (`extract_text()`) — works, with care

Page 1 of every report comes out as one line per holding, in table order. E.g. Top Ações p.1:

```
Commodities
Energia 18,1% 15,0% Petrobras PETR4 10,0% Compra R$ 63,00
PRIO PRIO3 5,0% Compra R$ 78,00
```

Weaknesses: sector/segment cells are on their own lines (`Commodities`, `Cíclicas \n Domésticas`);
a p.4 comment can interleave with the ticker row (`… em patamares \n elevados.`); the color-swatch
legend at the page edge (`126 126 126`, `255 188 0`, …) is included as text (it sits at negative x
coordinates, outside the page box).

### 5.2 pypdf layout text (`extraction_mode="layout"`) — best raw material for the four Top reports

Columns are preserved positionally, so each holding stays on one line with its ticker, weight,
rating and price, and the multi-line comments are still parseable by a ticker-anchored regex. This
is what the prototype used.

### 5.3 pdfplumber table extraction — unreliable, rejected

- Clean only when the table has full ruling lines: Top Ações p.1 (15×9), Top Dividendos p.1 (12×9),
  Small Caps p.1 (15×9) extract perfectly (merged `Segmento` cells become `null`s, which is even
  useful).
- Broken elsewhere: Top Ações p.4 detail table collapses every row into a single cell
  (`"…Petrobras PETR4 10,0% Compra R$ 63,00 retorno diante…"` + `null`s); Dividendos Plus p.1
  detects only the 1×4 header (body has no horizontal rules); FII p.2/p.4 split the two-line header
  into garbage cells (`"Perfo"`, `"rmance"`, a stray `"índic\nr alter\nnsal d\n1,25 p."`), misplace
  body values and drop body rows entirely.
- Spurious tables: the six color-swatch swatches (page edge, bbox x ≈ −75) are detected as a table
  on most pages of the four equity reports, and FII chart pages produce dozens of 1–4 row
  pseudo-tables.

### 5.4 Apache PDFBox 3.0.6 (the stack's existing library) — validated on the samples

`PDFTextStripper` output (both default and `setSortByPosition(true)`, `setStartPage/setEndPage` per
page) contains every holding row as a single self-contained line in all five reports. Running the
same anchored regexes over PDFBox output gives 16/16, 14/14, 14/14, 11/11 and 10/10 rows with
weight sums of 100.00% (Appendix B). `setSortByPosition(true)` additionally interleaves sector rows,
but that is harmless because the regex anchors on the ticker. `PDFTextStripperByArea` exists as a
fallback if a future template needs column-region separation. `PdfConverter.kt` in the repo already
uses `PDFTextStripper`, so this needs no new dependency.

### 5.5 OCR — not needed

Every page of all five PDFs has a real text layer (minimum 84 chars/page — the blank back covers;
the smallest content page is 696 chars, FII p.38). The large images are a 689×116 header banner
(page 1 of each report, FII p.2) and a 689×482 black back-cover image on the last page; charts are
small image fragments. Nothing that matters is rasterised.

### 5.6 Prototype result (evidence of parseability)

| Page | Rows found | Weight sum |
|---|---|---|
| Top Ações p.1 / p.4 | 14 / 14 | 100.0 / 100.0 |
| Top Dividendos p.1 | 11 | 100.0 |
| Top Small Caps p.1 | 14 | 100.0 |
| Dividendos Plus p.1 / p.3 | 10 / 10 | 100.0 / 100.0 |
| FII p.2 / p.4 | 16 / 16 | 100.0 / 100.0 |

(Same results with pypdf layout text and with PDFBox text. Regexes in Appendix B.)

---

## 6. Failure modes to design against

1. **Previous-month table on the performance page.** Top Ações p.3, Top Dividendos p.3, Small Caps
   p.3, Dividendos Plus p.5 all repeat every ticker with the *previous* weights. Anchoring must be
   `report family + expected page + header` — never "first table with a ticker".
2. **The portfolio table itself appears twice in the FII report** (p.2 and p.4), and once with an
   extra DY column in Dividendos Plus (p.3). Deduplicate by comparing parsed rows, not by taking
   the first.
3. **Tickers in prose and charts.** Top Ações prose names LREN3/ORVR3 (exited) and RDOR3/ITUB4/
   ROXO34 at their old weights; FII prose names 14 non-portfolio comparison funds; FII p.5 charts
   label all tickers. Only the portfolio table may feed weights.
4. **Ticker regex.** `B3SA3` breaks `[A-Z]{4}\d{1,2}` (10 rows/90% instead of 11/100% in Top
   Dividendos). Use `[A-Z][A-Z0-9]{3}\d{1,2}` (or a broker-validated pattern) and reject anything
   that does not look like a B3 code.
5. **Printed totals lie.** FII table total row prints `90%` while rows sum to `100.00%`. Never read
   a total row; compute the sum and require `100 ± 0.01`.
6. **Number format ambiguity.** `10,0%` vs `10.00%`; `R$ 686.45` (dot decimal) vs `R$ 63,00`;
   `1.621` (dot thousands); FII weights `9,00%`. Parse per column with a known format; refuse a row
   whose weight does not match `\d+([.,]\d+)?%`.
7. **Multi-line headers and cells.** `Cíclicas \n Domésticas`; FII's two-line header; `Desempenho
   desde \n entrada`; Small Caps `Link para \n Tese`. A parser should join wrapped header text or
   match on a normalised (whitespace-collapsed) header string.
8. **Header drift.** `Rating` vs `Recomendação`; `Preço-Alvo` vs `Preço Alvo`; `Desempenho YTD` vs
   `Desempenho 2026`. Anchor on a small required set of columns, ignore extras.
9. **Column-weight cells misplaced by extraction order.** PDFBox default order prints the sector row
   before its holdings; pypdf plain prints segment names alone. A row parser must tolerate
   standalone non-ticker lines and simply skip them.
10. **Comments/links on the detail table.** The comments are free prose with numbers, tickers and
    `Clique aqui` links; a "any line with a ticker and a %" scan would produce false rows (e.g. the
    Top Ações p.4 comment mentions `2T26`, `R$` values are absent but percentages are not).
11. **Junk "tables".** Color-swatch legend at negative x; chart fragments; FII chart pages' pseudo-
    tables. If a table API is used at all, filter by expected bbox/row count.
12. **Filename encoding.** Literal `&#8211` and accented characters in names; tests that glob
    fixtures must handle UTF-8 and the entity string.
13. **Duplicate/blank pages.** FII has a disclaimer page (39) and a mostly-empty back cover (40)
    that can carry the word `Disclaimer` and page numbers; scans must not treat them as content.
14. **Period phrasing varies.** `de X para Y` (Top reports), `p.p.` deltas (FII), `Entrada/Saída`
    only (Dividendos Plus). Do not attempt to derive new weights from prose.

---

## 7. Feasibility verdict and recommendation

### 7.1 Verdict per report type

| Report | Parse ticker + weight | Confidence | Notes |
|---|---|---|---|
| Top Ações | Yes | **High** | table p.1, ruled grid, stable header; ignore p.3 |
| Top Dividendos | Yes | **High** | same template; watch `B3SA3` |
| Top Small Caps | Yes | **High** | same template; `*11` names that are not FIIs |
| Top Dividendos Plus | Yes | **Medium-high** | different template; same weights (10%); DY column only on p.3 |
| FII Fundamentalista | Yes | **Medium-high** | duplicate table p.2/p.4; printed total wrong; target price only per-fund; tickers in prose/charts |

Rating/target/sector are secondary fields and parse per row where the column exists; they should
never block committing a snapshot.

### 7.2 Recommended approach

1. **Text layer, not OCR, not table grids.** Use Apache PDFBox 3.0.6 `PDFTextStripper`
   (`setSortByPosition` either way; verify per fixture) and slice by page ranges
   (`setStartPage/setEndPage`) to avoid cross-page bleed. If a future template needs it,
   `PDFTextStripperByArea` is available in the same jar.
2. **One parser per report family, selected by a `shouldExecute` predicate**, matching the
   repo's documented strategy-parser design (`backend/AGENTS.md`, "Format-specific parsing is a
   Strategy …", `http-api/.../strategyreport/`). Concretely: `TopPortfolioParser` (covers Top
   Ações/Dividendos/Small Caps via required header `Segmento … Companhia Ticker Peso`),
   `QuantitativePlusParser` (`Companhia … Ticker Peso Setor` + `10%`), `FundamentalistFiiParser`
   (two-line header `Peso % / Por Ticker` + `COMPRA`). Each parser is a small class with its own
   fixture test built from the real PDFs' PDFBox output.
3. **Anchor, then validate.** Locate the table by page + normalised header signature, then enforce:
   - at least 5 rows and no duplicate tickers;
   - all tickers match the B3 pattern;
   - weight sum = 100 ± 0.01;
   - every weight parses as a PT-BR percentage;
   - the same rows (ticker set + weights) must appear in the detail/second table when the report
     duplicates it (FII p.2 vs p.4; Dividendos Plus p.1 vs p.3), otherwise it's a hard failure.
4. **Never trust prose, totals, or previous-month tables.** Compute deltas by diffing the newly
   parsed snapshot against the previously accepted snapshot. Prose "changes" and p.3 tables can be
   surfaced as *hints* in the review UI but must not set weights. The FII's printed total is
   provably wrong (`90%` vs 100,00%).
5. **Manual override is a first-class step, not an error path.** Persist a candidate snapshot
   (strategy, reference month, source file, page, parsed rows + warnings) and require the user to
   confirm or edit (add/remove ticker, fix weight) before it becomes the input to rebalancing.
   This absorbs template drift, CSV/PDF re-download quirks, and month-specific oddities.
6. **Refuse to guess when**: the header anchor is absent or ambiguous; rows fail the ticker
   pattern; the sum is not 100; two candidate tables disagree; the report family is not
   recognised; a rating value outside the known set (`Compra`, `Neutro`, `Venda`, `COMPRA`, …)
   appears; or the file's publication date cannot be read. In all of those, fail the parse with a
   clear error and keep manual entry possible.

### 7.3 Impact on the rebalancing engine

- Model the parsed unit as an immutable **strategy snapshot**: `{strategy, referenceMonth,
  publicationDate, source, rows: [{ticker, targetWeight, rating?, targetPrice?, sector?}]}`. The
  engine compares two snapshots; it never reads PDF prose.
- The report's **data base (31/08/2026) and publication date (01/09/2026) differ** — decide which
  one defines the rebalance effective date, and store both.
- Because Dividendos Plus is equal-weight and FII changes are expressed in p.p., the engine must
  not assume delta semantics; only target weights are portable across report families.
- A user's manual override changes the snapshot, not the PDF: re-parsing the same file must yield
  the same candidate, with the override applied on top (auditable diff).
- The user's actual holdings may not match last month's report (they might have skipped a
  rebalance). Deltas for display should therefore be computed against the **user's last accepted
  snapshot**, not against the report's previous-month table.

### 7.4 Residual risks / open uncertainties

- **Single edition.** Only one month (September 2026) per report exists in the samples; month-to-
  month template drift cannot be measured. The repository's `backend/AGENTS.md` claims the parsers
  were verified against real September 2026 files, which matches this batch, but future months
  should be treated as potentially breaking (hence the manual confirm step and fixture tests).
- **"Não operar" is unobserved** — the term is absent from all five samples (text and raw content
  streams). If it appears in other XP products, the parser needs a new anchor and a value mapping.
- **PDFBox line layout is slightly different from pypdf's** (sector rows interleaved), but rows
  parse; the equivalence was verified for the eight table cases above. Fixture tests should pin the
  PDFBox output the Kotlin parser actually sees.
- **No OCR validation needed**, but if XP ever ships an image-only PDF version, parsing must fail
  loudly (no text layer) instead of silently returning nothing.
- The repo's `backend/AGENTS.md` describes a `http-api/.../strategyreport/` package with three XP
  layout parsers plus a generic fallback, but that package is not present in the current checkout
  (a `strategy-reports` branch exists). Any code already written there should be reconciled with
  this document's field/trap list before merging.

---

## Appendix A — probe commands and specific observations

Enumerate the samples (note the literal `&#8211` and non-ASCII names):

```powershell
py -3 -c "import os; d=r'references/xp relatorios'; print('\n'.join(repr(f) for f in os.listdir(d)))"
# 'Carteira Top Dividendos &#8211.pdf'
# 'Carteira Top Dividendos Plus &#8211.pdf'
# 'Carteira Top Small Caps &#8211.pdf'
# 'Carteira-Fundamentalista-XP-09-2026-3.pdf'
# 'Top Ações XP &#8211.pdf'   (console shows Top A??es due to codepage; bytes are UTF-8)
```

pypdf baseline and extraction modes:

```powershell
py -3 -c "import pypdf,sys; print(pypdf.__version__, sys.version)"
# 6.18.0 3.13.5
```

`Rotated text discovered` in layout mode fires on: Top Dividendos p.3; Dividendos Plus p.4; FII
pages 5,7,9,11,12,13,15,17,18,19,20,21,22,23,24,25,26,27,29,31,32,33,34,35,36,37 — never on a
portfolio-table page.

Word-level diff pypdf vs pdfplumber (page by page) shows only benign tokenisation differences
(`1EQUITY` vs `1` + `EQUITY`, chart labels re-joined differently, `previstasna`, etc.) — no table
text is lost by pypdf.

Raw stream search for `operar`:

```python
for i, page in enumerate(reader.pages, 1):
    data = page.get_contents().get_data()
    if b"operar" in data.lower(): ...   # 0 hits in all five files
```

## Appendix B — prototype parser (evidence)

Ran against pypdf layout text and PDFBox text, identical results:

```python
TICK = r"[A-Z][A-Z0-9]{3}\d{1,2}"                      # B3SA3 requires a digit-capable class

def parse_top(line):        # Top Ações / Top Dividendos / Top Small Caps
    return re.search(rf"({TICK})\s+(\d+,\d+)%\s+(Compra|Neutro|Venda)\s+R\$\s*([\d.,]+)", line)

def parse_dividendos_plus(line):
    return re.search(rf"({TICK})\s+(\d+)%\s+(\S.*)$", line.strip())

def parse_fii(line):        # weight first, sector/name in the middle
    return re.search(
        rf"(\d+,\d+)%\s+(\S.*?)\s+({TICK})\s+(COMPRA|NEUTRO|VENDA)\s+(.+?)\s+"
        rf"([\d.]+)\s+(\d+)\s+(\d+)\s+(\d+)%\s+(-?[\d,]+%)\s+(-?[\d,]+%)\s+([\d,]+%)\s+(\d+)\s*$",
        line.strip(),
    )
```

Prototype outputs: 14/11/14/10/10/16 rows, weight sums 100.0 in every case (see §5.6). The first
version with `[A-Z]{4}\d{1,2}` missed `B3SA3` and produced 10 rows / 90.0% for Top Dividendos —
which is exactly the kind of hard-fail validation the parser should enforce.

PDFBox extraction command (from the temp dir, using the already-cached jars):

```powershell
javac -cp pdfbox-3.0.6.jar;fontbox-3.0.6.jar;pdfbox-io-3.0.6.jar;commons-logging-1.3.5.jar ExtractXP.java
java  -cp "<same cp>" ExtractXP
```

(The `.java` file and its outputs live in the temp folder; nothing was installed into or written
inside the repo besides this document.)
