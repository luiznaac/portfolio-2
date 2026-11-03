# Redesign prototype fixture

One shared mock dataset for the frontend-redesign prototypes tracked by the wayfinder map (#347).
Every prototype imports the same module, so the same portfolio, month and numbers appear on every
screen. The shapes mirror the backend edge DTOs in [`frontend/src/api/types.ts`](../../src/api/types.ts);
[a prototype can later be wired](#wiring-it-to-the-real-api) without reshaping the data.

```js
import fixture from "./fixture.js";

const { allocation, orderPlan, orderList, summary, attention } = fixture.portfolio;
```

`fixture.js` is a dependency-free ES module; open an HTML prototype with
`<script type="module">` and import it directly. No build step, no server, no persistence.

## The scenario

Rafael is a single power user running a R$ 1.5M portfolio across Ações, FIIs and Renda Fixa. The
fixture is the state on **2 November 2026**: October is closed, November is the open month and its
plan is already computed. The story the numbers tell:

- Two report strategies feed the Ações class (`Dividendos BR`, `Qualidade Global`) and one feeds the
  FIIs (`Fundos de Papel`); a legacy manual bucket still holds the remaining Ações and FIIs money.
- The FIIs and the dividend sleeve are **over** their targets; the global-equity sleeve, the
  inflation-linked RF and the family bucket's WEGE3 target are **short**. The November plan rotates
  by buying more AAPL34/MSFT34, funding the buys with a partial KNCR11 sale, and suggesting a WEGE3
  attribution transfer out of the legacy bucket.
- October was nearly flat (+0.12%) against a +1.75% benchmark, and its stock sales crossed the R$20k
  exemption: a R$255.00 DARF is due on 30 November.
- Three data-quality stories are open: a staged B3 movements import with one bad row, a WEGE3 split
  waiting for review, and an AAPL34 custody gap with a pending realized result. The staged XP
  editions for November show the report-promotion flow.

## Numbers

All money is BRL. Drift is in percentage points; targets and weights are percentages (50 means 50%).

### Holdings

| Class | Ticker | Quantity | Close | Value |
| --- | --- | ---: | ---: | ---: |
| Ações | ITSA4 | 12,000 | 10.10 | 121,200.00 |
| Ações | BBAS3 | 4,000 | 27.40 | 109,600.00 |
| Ações | VALE3 | 1,500 | 61.20 | 91,800.00 |
| Ações | PETR4 | 3,000 | 38.90 | 116,700.00 |
| Ações | AAPL34 | 2,000 | 60.00 | 120,000.00 |
| Ações | MSFT34 | 800 | 88.00 | 70,400.00 |
| Ações | WEGE3 | 1,000 | 52.40 | 52,400.00 |
| Ações | EGIE3 | 1,200 | 41.10 | 49,320.00 |
| FIIs | MXRF11 | 20,000 | 10.12 | 202,400.00 |
| FIIs | KNCR11 | 1,500 | 104.30 | 156,450.00 |
| FIIs | HGLG11 | 300 | 162.80 | 48,840.00 |
| Renda Fixa | Tesouro IPCA+ 2035 | — | — | 170,030.10 |
| Renda Fixa | CDB Pré 2028 | — | — | 120,000.00 |
| Renda Fixa | Conta Remunerada CDI | — | — | 60,000.00 |

Class totals: Ações 731,420.00 · FIIs 407,690.00 · Renda Fixa 350,030.10 ·
**holdings 1,489,140.10**. November's Capital is 1,510,000.00 and its Floor is 10,000.00, so the
Uninvested Balance is **20,859.90**. VALE3's close is dated 24/10 (stale); NVDC34 is in the catalog
but has no quote and no position.

### Allocation (GET /month/current/allocation)

The allocation chain scales against **Capital** (1,510,000), as the backend read does.

| Rung | Target | Ideal | Actual | Deficit | Drift |
| --- | ---: | ---: | ---: | ---: | ---: |
| Ações | 50 | 755,000.00 | 731,420.00 | 23,580.00 | −1.56 |
| — Dividendos BR | 52 | 392,600.00 | 439,300.00 | −46,700.00 | +3.09 |
| — Qualidade Global | 30 | 226,500.00 | 190,400.00 | 36,100.00 | −2.39 |
| FIIs | 25 | 377,500.00 | 407,690.00 | −30,190.00 | +2.00 |
| — Fundos de Papel | 74 | 279,350.00 | 358,850.00 | −79,500.00 | +5.26 |
| Renda Fixa | 25 | 377,500.00 | 350,030.10 | 27,469.90 | −1.82 |
| **Portfolio** | — | — | 1,489,140.10 | 20,859.90 | −1.38 |

The manual buckets are why the class Actuals exceed the report strategies: Ações carries Legado
Ações (101,720.00) and FIIs carries Legado FIIs (48,840.00). Those buckets have no Target, so they
are `manual` rows, not strategy rungs. The class deficit sums to the portfolio deficit.

### Order plan (GET /month/current/orders?phase=full)

The order chain scales against **Investable Capital = Capital − Floor = 1,500,000**, so its ideals
are slightly smaller than the allocation's (Ações 750,000 vs 755,000; Qualidade 225,000 vs 226,500).

- Buys: AAPL34 `floor(135,000 / 60.00) = 2,250` target units, 2,000 held → **250 units = 15,000.00**;
  MSFT34 `floor(90,000 / 88.00) = 1,022` target units, 800 held → **222 units = 19,536.00**.
  Buy demand = **34,536.00**.
- Deployable cash = Uninvested Balance 20,859.90 − Floor 10,000.00 = **10,859.90**.
- Shortfall = 34,536.00 − 10,859.90 = **23,676.10**. The largest surplus pool is KNCR11
  (`1,500 − 1,064 = 436` units over its whole-unit target), and **227 × 104.30 = 23,676.10** covers
  it exactly. `unfunded_amount` is 0, so the pro-rata factor stays 1.
- RF orders at the full phase: FLOATING_RATE contributes 7,500.00, INFLATION_LINKED contributes
  17,469.90; FIXED_RATE is exactly at its ideal.
- Attribution moves: +250 AAPL34 and +222 MSFT34 to Qualidade Global, −227 KNCR11 from Fundos de
  Papel.
- One transfer suggestion: **372 WEGE3** (`floor(19,500 / 52.40)`) from Legado Ações to Dividendos
  BR, worth 372 × 52.40 = **19,492.80**. It waits for a human because the bucket is the donor; the
  same proposal is persisted as `orderList.transfers[0]` with status `PENDING`.

The persisted `orderList` is the November plan's lifecycle view: three `SUGGESTED` orders, the
pending WEGE3 transfer and no unplanned trades yet. The October snapshot keeps the previous month's
lifecycle instead: two `IMPORTED` orders (PETR4 and KNCR11) and one unplanned VALE3 sale.

### Performance

The series covers 12 closed months, November 2025 to October 2026. Months before the first close
(August 2026) are `computed`; August, September and October are `snapshot` months. The backend's
snapshot-era decomposition uses the month's **Capital** as `value_end`, so:

| Month | Value start | Value end | Aportes | Income | Return | Benchmark |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 2026-08 | — | 1,450,000.00 | 20,000.00 | 2,400.00 | — | +0.61 |
| 2026-09 | 1,450,000.00 | 1,485,000.00 | 30,000.00 | 2,800.00 | +0.15 | −0.51 |
| 2026-10 | 1,485,000.00 | 1,500,000.00 | 10,000.00 | 3,200.00 | +0.12 | +1.75 |

October: `(1,500,000 − 1,485,000 − 10,000 − 3,200) / 1,485,000 = 0.12%`. The benchmark blend is
`0.75 × 2.10 (IBOV) + 0.125 × 0.95 (CDI) + 0.125 × 0.42 (IPCA) = 1.75%`.

October's class detail (`analysis.monthPerformance`) starts from September's class Actuals and
allocates the month's aportes by class target, the income by ticker:

| Class | Start | End | Aportes | Income | Return |
| --- | ---: | ---: | ---: | ---: | ---: |
| Ações | 727,260.00 | 731,420.00 | 5,000.00 | 1,050.00 | −0.26 |
| FIIs | 406,186.00 | 407,690.00 | 2,500.00 | 2,150.00 | −0.77 |
| Renda Fixa | 348,910.00 | 350,030.10 | 2,500.00 | 0.00 | −0.40 |

Strategy returns: Dividendos BR −2.02, Qualidade Global +2.13, Legado Ações +3.23, Fundos de Papel
−1.00, Legado FIIs +0.87. The series' `xirr` (4.1) covers the snapshot era; `computed_xirr` (9.2)
covers the computed era. Both are illustrative, not derived from the monthly rows.

Income rows are a 13-month ledger: Nov 2025 through Oct 2026 are `RECEIVED`/`RECONCILED`, and the
November 2026 rows are `PAYABLE` (BBAS3 JCP, 510.00) and `ANNOUNCED` (MXRF11, 1,600.00). Each closed
month's income rows sum to the `net_income` in the series (their `date` decides the month).

### Tax (October 2026)

- STOCK gross sales 24,600.00: PETR4 500 × 38.00 and VALE3 100 × 56.00.
- Realized gain 2,400.00: PETR4 500 × (38.00 − 34.00 fiscal average) = 2,000.00; VALE3
  100 × (56.00 − 52.00) = 400.00.
- The R$20k exemption is crossed, so the whole month's gain is taxable after the SWING carry:
  2,400.00 − 700.00 = 1,700.00 → 15% = **255.00**, DARF due 30/11/2026.
- FII sales sit outside the meter: KNCR11 40 × 107.80 = 4,312.00 gross, 384.00 gain; the FII carry
  absorbs the 384.00 and closes at 416.00. The SWING carry closes at 500.00.
- One day-trade warning (PETR4, 14/10, 200 units — a warning only) and one open AAPL34 gap of 50
  units whose realized result is pending.

`analysis.currentTax` is the open November report: empty meter, the October carry as opening
balances, and the same open gap. Its DARF deadline is 31/12/2026.

### Imports and attention

| Import | Source | Status | What it holds |
| --- | --- | --- | --- |
| 101 | B3_MOVEMENTS | STAGED | 4 rows: one new ITSA4 buy, its in-file duplicate, a PETR4 sale already in the ledger, one unknown-ticker error |
| 102 | B3_POSITION | REVIEWED | Custody snapshot, checked and ready to apply |
| 103 | B3_TRADES | APPLIED | The three October sales (VALE3, PETR4, KNCR11) |
| 104 | XP_REPORT | FAILED | A PDF attempt that failed; the re-exported CSV produced the staged Dividendos BR edition |

`imports.editionImport` is the XP report import response: a parser name plus staged edition 104.
`imports.movementsImportResult` describes an earlier movements import that carried trades, income
and two applied corporate actions. There is also one WEGE3 split in `NEEDS_REVIEW` (1:2, ex
20/10/2026) and one AAPL34 open gap.

`portfolio.attention` is a **derived convenience list**, not a wire DTO: eight items assembled from
the imports, staged editions, pending transfer, gap, stale VALE3 quote and payable BBAS3 income. It
is ordered action-first and each item carries a `route` a prototype can treat as a link.

## Snapshot months

| Snapshot | Capital | Actual | Caixa |
| --- | ---: | ---: | ---: |
| 2026-08 | 1,450,000.00 | 1,448,866.00 | 1,134.00 |
| 2026-09 | 1,485,000.00 | 1,482,356.00 | 2,644.00 |
| 2026-10 | 1,500,000.00 | 1,489,140.10 | 10,859.90 |

August and September hold the quantities **before** the October sales: PETR4 3,500, VALE3 1,600
and KNCR11 1,540. October holds the post-sale quantities (3,000 / 1,500 / 1,500). Each state's
class totals, strategy slices, income, orders and tax roll up to its Actual.

## What each screen reads

| Prototype screen | Fixture key |
| --- | --- |
| Portfolio landing | `portfolio.summary`, `portfolio.allocation`, `portfolio.attention` |
| Strategies list and detail | `strategies.rows`, `strategies.editions`, `strategies.holdings`, `strategies.attribution`, `strategies.performance` |
| Assets | `assets.catalog`, `assets.quotes`, `assets.positions`, `assets.positionDetails`, `assets.gaps`, `assets.fixedIncome` |
| Imports | `imports.items`, `imports.rows`, `imports.editionImport`, `imports.corporateActions`, `imports.movementsImportResult` |
| Plan / Month | `plan.currentMonth`, `plan.allocation`, `plan.orderPlan`, `plan.orderList`, `plan.months` |
| Analysis — Performance | `analysis.performanceSeries`, `analysis.monthPerformance` |
| Analysis — Proventos | `analysis.income` |
| Analysis — Impostos | `analysis.tax`, `analysis.currentTax` |
| History / month detail | `snapshots` |

`portfolio` and `plan` share the same objects by reference (`allocation`, `orderPlan`, `orderList`,
`currentMonth`), so a prototype can read either key without copying.

## Wiring it to the real API

The wire-shaped keys keep the backend's snake_case field names, enums and value types, so replacing
the import with fetches is a data-source change, not a screen rewrite. The non-wire conveniences are
explicit:

- `portfolio.attention` and `portfolio.summary` are derived for the prototypes; the real screens
  would compute them from the same reads.
- `assets.fixedIncome.positions` is the consolidated RF read; the backend exposes it as an
  aggregation, not as a stored DTO.
- `assets.recentTrades` is a **window** of recent trades, not the full ledger. `assets.positionDetails`
  is complete for PETR4 and KNCR11 and reconciles to their positions (PETR4:
  1,000 × 33.60 + 2,500 × 34.16 = 119,000 / 3,500 = 34.00 average, then 500 sold; KNCR11:
  1,540 bought, 40 sold).
- `strategies.performance` is the October strategy slice of `analysis.monthPerformance`.
- `assets.catalog` is a window: the tickers the portfolio can reach plus the one staged NVDC34
  entry, not the 412-entry COTAHIST catalog the refresh result reports.

Not modelled: authentication and users, benchmark point series (only monthly index returns), RF
product histories, and notification delivery.

## Shell prototype (wayfinder ticket #350)

[`shell-prototype.html`](shell-prototype.html) is the shell, navigation and landing prototype. It
imports `fixture.js` directly and needs no build step; serve the folder so the module import works:

```
python3 -m http.server 8765 -d frontend/design/redesign
# then open http://localhost:8765/shell-prototype.html
```

Everything is switchable from the bottom bar and preserved in the URL:

- `?v=a` — flat six-object sidebar; the active object's sub-views open beneath it.
- `?v=b` — **chosen**: six objects grouped Posição / Operações / Resultados; sub-views as page tabs.
- `?v=c` — no sidebar; objects in a top rail, landing as a three-column cockpit.
- `?p=tabs|sidebar|segment` — on an object page, the three sub-view placements.

The landing decisions carried forward: portfolio status (Capital, invested, cash, drift) and the
attention queue above the fold, attention in the right rail grouped **Ação → Acompanhar**, and the
allocation table showing classes only — strategy drift lives on the Alocação page, not the landing.

## Strategy prototype (wayfinder ticket #351)

[`strategy-prototype.html`](strategy-prototype.html) compares two treatments for surfacing multiple
strategies on the same fixture. Serve the folder as above and open
`http://localhost:8765/strategy-prototype.html` or `...?v=a&d=compare` to open the comparison drawer
on load.

- `?v=a` — **treatment A**: strategies as first-class objects. A comparable list, then a detail page
  with tabs **Carteira / Edições / Atribuição / Fatia no portfólio**. Manual strategies are the same
  object with a reduced tab set and a "sem alvo" treatment.
- `?v=b` — **treatment B**: strategies as a lens on the Alocação page. The lens rail filters the same
  allocation table; a context rail carries edition diff, attribution, performance and portfolio share.
  Manual strategies become "sem alvo" lenses.
- `d=compare` — opens the A/B comparison drawer on load.

Both treatments carry the same material: the **Relatório vs Manual** distinction, the staged
**edition diff** (`104`, `203`, `303`), **attribution across strategies**, the portfolio-slice and
performance reads, and the WEGE3 transfer suggestion. The bottom bar's scenario buttons jump to the
list, a detail/lens, the diff, attribution and a manual bucket, so the owner can compare like for
like.

**Decided in #351: treatment A.** Strategies stay first-class objects with list → detail → tabs; B's
lens is not carried forward. Two follow-ups from the click-through are already in the prototype: the
Edição card left the *Fatia no portfólio* tab, and the cross-strategy table is now labelled
**Reconciliação custódia × atribuição** (its job is spotting split or unattributed custody, not
repeating the strategy's own attribution). The losing treatment stays in this file as comparison
evidence only; the assembled prototype (#357) and the spec (#358) take A.

## Plan prototype (wayfinder ticket #352)

[`plan-prototype.html`](plan-prototype.html) treats Plan / Month as a tool you open when you act, on
the same fixture. Serve the folder as above and open
`http://localhost:8765/plan-prototype.html`; append `?v=a|b|c&l=quiet|card|attention&s=fresh|mid|partial|ready|closed`
to reproduce any state. The default is the decided combination: **B + silencioso**, 6-step progress.

Three plan surfaces (bottom bar **Plano**):

- `?v=a` — **flow**: the whole month on one scrolling page (capital, target chain, plan with funding
  and attribution, execution, checks, close), with a sticky progress rail and "resume at the open
  step".
- `?v=b` — **steps** (decided): six numbered steps, only the active one on screen; opening the plan
  lands on the first unfinished step and concluded steps collapse to one-line summaries.
- `?v=c` — **next action**: the plan opens on the single thing that needs the owner — the pending
  transfer decision, the unexecuted orders, or the close gate — with the whole month behind "ver o
  mês inteiro".

Three ways the open month reaches the landing (bottom bar **Na carteira**):

- `l=quiet` (decided) — one status line under the KPI strip; no card, no extra badge.
- `l=card` — a compact month card below the allocation: progress, next action, "continuar de onde parei".
- `l=attention` — nothing new: the pending transfer is an attention action and unexecuted orders join
  the queue; the plan has no CTA of its own.

Five month states (bottom bar **Cenário**): `fresh` (0/3 executed), `mid` (1/3), `partial` (AAPL34
120/250 and the transfer rejected), `ready` (3/3, transfer decided), `closed` (snapshot frozen). Approving/rejecting the WEGE3 transfer and closing the month
update the gate and the landing card live; switching scenario resets the decisions.

Domain behaviour the prototype encodes, from the backend rather than invented:

- One open month at a time, by calendar; opening another month closes the one still open.
- A buy/sell partially executed stays `SUGGESTED` with `executed_quantity` beside the proposed one;
  a covered order is `EXECUTED`, and `IMPORTED` when the matched trade came through a B3 import.
  There is no per-order "não operar": a deliberately skipped order looks exactly like one the broker
  has not reported yet.
- The transfer decision is atomic: approve moves the whole quantity, reject moves none. Only a
  `PENDING` proposal blocks the close; a rejected one is a decision and unblocks it.
- The close recomputes the plan first — which is what finalises attribution transfers, auto-approving
  those below R$ 500 — then refuses while a `PENDING` transfer remains, and otherwise locks the month
  and writes the immutable Snapshot.
- Only an undecided transfer blocks the close. Open gaps, a corporate-action review, a stale quote and
  unexecuted or partially executed orders are frozen as they are, and the closing screen says so.
- The transfer is attribution-only (custody untouched) and a manual bucket donor always waits for a
  human, whatever the amount.
- The target chain is Capital × Target at every level; a plan in the store is a snapshot of its last
  computation, so editing Capital previews the chain but only a refresh moves the orders.

Not modelled: editing the target chain (POST /month/current/targets), opening the next month, the
manual order-match override, and the visual direction (#355).

**Decided in #352: B + silencioso.** Plan / Mês opens as six steps (capital, alvos, plano, ordens,
transferência, fechamento), landing on the first unfinished one, with a 6-step fraction (`3 de 6`)
and segmented progress bar. On the Carteira landing the open month is one status line under the KPI
strip — no card, no extra badge; a blocking transfer is the one thing that earns a plan badge. A's
flow and C's next-action card stay in this file as comparison evidence only.

The close rule the spec carries: **only an undecided transfer blocks Fechamento**; a rejected
proposal is a decision and unblocks it. Partial or unexecuted orders, an open custody gap, a stale
quote and a corporate-action review freeze into the snapshot as they are. A self-declared "won't
trade this" order state does not exist yet and stays a recorded gap — the close screen lists the
unexecuted orders instead of pretending to tell them apart from unreported ones. A partial execution
is a display-only label derived from `executed_quantity`; the persisted order status stays
`SUGGESTED` until fully covered.

## Analysis prototype (wayfinder ticket #353)

[`analysis-prototype.html`](analysis-prototype.html) compares where performance, income and taxes
live, on the same fixture. Serve the folder as above and open
`http://localhost:8765/analysis-prototype.html`; append
`?v=a|b|c&per=1m|3m|6m|12m|year&anchor=YYYY-MM` to reproduce any state. The default is **A**, 12
months anchored on the last closed month (out/26).

Three treatments (bottom bar **Tratamento**):

- `?v=a` — **one Analysis area, three views**: Análise in the sidebar with Performance / Proventos /
  Impostos as page tabs over one shared period rail. Switching tabs keeps the window.
- `?v=b` — **three destinations**: Performance, Proventos and Impostos are their own sidebar objects
  under Resultados. Each destination owns its period; the rail is labelled "período desta tela".
- `?v=c` — **Análise with a resumo**: a Resumo tab answers "como fui / o que recebi / o que devo" in
  the shared window, with a month-by-month table carrying the three reads side by side, then the
  same three views for the detail.

The period rail is the object under comparison: presets (mês, 3, 6, 12 meses, ano), a month arrow on
each side, and the month-anchor. Bottom bar scenarios jump to **Como fui**, **O que recebi**, **O que
devo**, **Trocar período** (sets 3 months and moves to Proventos, so you can see whether the window
travels) and **Abrir do alerta** (the "JCP de BBAS3 a receber" attention item, which lands in
Proventos with the row highlighted).

Grain behaviour the prototype encodes, from the fixture rather than invented:

- Performance and Proventos are **window** reads: the monthly series is filtered to the rail's range,
  and the chained return is the window's own (base 100 at the start).
- Impostos is a **monthly** read: `GET /tax/{period}` exists per month, so the rail selects the
  month-anchor there. The window only filters which apurações the side panel lists.
- "A receber" in Proventos is forward-looking: an `ANNOUNCED`/`PAYABLE` row shows even when its
  payment date falls just outside the window, marked "fora do recorte".
- The benchmark blend only exists from ago/26, so a 12-month window labels it "blend cobre 3 de 12
  meses" instead of comparing a 12-month return with a 3-month one.
- The fixture carries two tax apurações (out/26 fechado, nov/26 aberto); the window's other months
  show an empty DARF cell rather than a fabricated zero.

Not modelled: editing targets, the harvest UI (the Impostos view is read-only here), the visual
direction (#355) and the additive read models a combined resumo would need.

**Decided in #353: treatment A — one Análise with three sibling views.** Performance / Proventos /
Impostos stay page tabs under one Análise object, over a single shared period rail. The rail opens on
**12 months ending the last closed month** and keeps no cross-visit memory; within the session the
window survives a tab switch, which is the point of the shared rail. The monthly grain of the tax read
is handled inside that rail: the month-anchor selects the apuração and the window only filters which
months the side panel lists.

What the decision drops, and why:

- B's three destinations are not carried forward: the period context would have to be re-established
  on every jump between the three questions.
- C's resumo landing is not carried forward either. It reads well, but the owner opens the area
  already knowing the question — the tabs at the top of A are the cheaper surface, and C would add a
  combined month-by-month read model with no screen explicitly asking for it.
- Because Proventos is a tab, the "a receber" bubble stays inside it, and the attention deep link
  (`analysis/income/24`) lands in the same shared window instead of a separate destination.

The losing treatments stay in this file as comparison evidence only; the assembled prototype (#357)
and the spec (#358) take A.
