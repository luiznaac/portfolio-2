---
source: luiznaac/portfolio-2#120
branch: research/corporate-actions
date: 2026-09-16
---

# Research: corporate actions modeling — kinds, quantity/avg-price effects, sources

- **Ticket:** [luiznaac/portfolio-2#120](https://github.com/luiznaac/portfolio-2/issues/120) — "Research: corporate actions modeling (non-blocking)"
- **Date:** 2026-09-16
- **Branch:** `research/corporate-actions` (worktree `../.worktrees/portfolio-2-research-corp-actions`)
- **Question:** How should corporate actions be modeled — kinds (splits, bonificações, grupamentos, incorporações/amalgamações), their effects on a listed asset's quantity and average price, and their sources (B3 Movimentação export, manual entry)?
- **Consumers:** the later decision on whether `CorporateAction` becomes a first-class domain concept (domain model today covers `Income`/proventos only); grilling ticket [#101](https://github.com/luiznaac/portfolio-2/issues/101).

## Answer (short)

- **Two families, different math.** B3's pricing manual splits events into **(a) cash events** — dividendos, JCP, **bonificações em recursos financeiros** (cash bonus), restituição de capital, juros/rendimentos — which move the ex-price by the cash amount (`P_ex = P_com − X`), and **(b) asset events without changing the underlying asset** — **grupamento, desdobramento, bonificação em ativos** — which move the ex-price by a ratio (`P_ex = P_com / Q`) or bonus factor (`P_ex = P_com / (1+B)`). This is the single best public source of B3's own event model. Sources: B3 Manual de Precificação de Eventos Corporativos v13, §1.1–1.2 (§6 below).
- **Fiscal effect on cost basis is law, not convention.** For a PF investor: **desdobramento → the added shares have zero cost** (total stays, quantity grows; IN RFB 1.585/2015 art. 58 §7 II; RFB Q&A 722); **grupamento → the inverse, total cost preserved** (no explicit RFB clause found; quantity falls, avg price rises — same invariant); **bonificação em ações → cost = the capitalized profit/reserve amount attributed to the shareholder** (IN 1.585 art. 58 §1; RFB Q&A 721); **incorporação/fusão/cisão → original cost is allocated to the new shares in the proportion fixed by the assembly** (IN 1.585 art. 58 §6); **redução de capital com restituição em dinheiro → the amount received reduces the cost basis** (IN 1.585 art. 58 §8).
- **The tracker math is simple for the common cases:** split multiplies quantity and divides average price; grupamento divides quantity and multiplies average price (fractions from either are auctioned for cash, not held); bonificação em ações raises quantity and recomputes avg from (total cost + declared capitalized value)/new quantity; incorporação/cisão swaps the position and carries cost proportionally; cash events (bonificação em dinheiro, restituição, amortização) leave quantity alone and either reduce cost (restituição, per §8) or are cash received (treatment of bonificação em dinheiro not found in primary sources — flag).
- **Sources for the app:** the **Negociação export has no corporate events** (re-verified today: 82 rows, only `Compra`/`Venda`, no event keyword traces). Corporate events are documented only for the **Movimentação** dataset ("aplicações, eventos e vencimentos" — B3 product page; the Movimentação API lists "Eventos Corporativos (pagamento de dividendo, grupamento, desdobramento)" — B3 API manual) and, for the money side, the **Eventos Provisionados** API (`BONIFICAÇÃO EM DINHEIRO`, `RESTITUIÇÃO DE CAPITAL`, `AMORTIZAÇÃO`, …). The Movimentação XLSX schema is documented only by a **secondary** guide (Cota B3): its `Tipo de Movimentação` column includes `Grupamento`, `Desdobramento`, `Transferência` — **verify the value domain in-account**; nothing published says how a ratio or resulting quantity is encoded in a row.
- **Recommendation for the later decision:** `CorporateAction` is worth first-class status, but it needs two axes — **position effect** (quantity factor/delta, cost effect) and **cash effect** (money received, tax nature). Cash-like corporate actions (bonificação em dinheiro, restituição de capital, FII amortização, reembolsos) overlap with `Income`; the decision should place them once, with a link, not model them twice. Sketch in §4.

## 1. Taxonomy — kinds and effects (ações, FIIs, BDRs, ETFs)

Definitions and ex-price behavior from B3's **Manual de Precificação de Eventos Corporativos v13** (public, 31/07/2024) and the fiscal effect from **IN RFB 1.585/2015**, **Lei 9.249/1995** and **RFB "Perguntas e Respostas IRPF 2026"**.

| Kind (pt-BR) | What it is | Quantity effect | Average-price / cost effect (fiscal) | Primary source |
|---|---|---|---|---|
| **Desdobramento** (split) | Same asset; quantity multiplied by ratio `Q`, price divided by `Q`; no cash, no new underlying | × `Q` | Added shares cost = **zero**; total cost unchanged; avg = old avg ÷ `Q` | B3 Manual §1.2 (eq. 1.3, `P_ex = P_com/Q`); IN 1.585 art. 58 §7 II; RFB IRPF Q&A 722 |
| **Grupamento** (reverse split) | Quantity divided by ratio `Q`, price × `Q`; fractions are **not** kept — they are auctioned and paid in cash | ÷ `Q` (floor) + cash for fraction | Total cost preserved (inverse of split; no RFB question found in the 2026 Q&A — flag as derived invariant) | B3 Manual §1.2 (eq. 1.3); B3 MGLU3 grupamento edital (fraction auction); RFB Q&A has no grupamento item |
| **Bonificação em ações** (bonus shares) | Company capitalizes lucros/reservas and gives free shares; ratio `B` per share held | × (1 + `B`) | Cost of the bonus participation = **the capitalized profit/reserve amount attributed** (declared value); total cost = old + declared; 1994/95 profits → zero | B3 Manual §1.2 (eq. 1.2, `P_ex = P_com/(1+B)`); IN 1.585 art. 58 §§1–2; Lei 9.249/1995 art. 10, parágrafo único; RFB Q&A 721 |
| **Bonificação em dinheiro** / em recursos financeiros | Cash distribution formally classified as capitalization bonus | unchanged | B3 prices it as a **cash event** (`P_ex = P_com − X`); no RFB rule found in the 2026 Q&A for PF cost basis — **treat as cash received, tax treatment to verify** (flag) | B3 Manual §1.1; B3 Eventos Provisionados API domain (`BONIFICAÇÃO EM DINHEIRO`) |
| **Restituição / redução de capital** (cash) | Company returns capital in cash | unchanged | The amount received **reduces the acquisition cost** of the shares | B3 Manual §1.1 (restituição de capital as cash event); IN 1.585 art. 58 §8 |
| **Subscrição** (rights issue) | Shareholder may buy new shares at price `K`, with/without bônus de subscrição; voluntary event | + subscribed quantity (if exercised) | New shares' cost = price actually paid (`K` × qty) + cost of any rights acquired; avg recomputed over the combined position | B3 Manual §§2–4 (ex-price and right pricing); B3 clientes article (voluntary vs involuntary) |
| **Incorporação / fusão / cisão** (share swap) | Corporate reorganization; shares of one company exchanged for shares of another (and/or cash) | Position replaced / added per assembly ratio; possibly multiple new tickers | **Original cost is attributed to the new shares in the same proportion fixed by the assembly**; cash portion is an alienation (taxable if value > declared cost at transfer) | IN 1.585 art. 58 §6; RFB IRPF Q&A 603 (incorporação de ações as alienation); B3 Manual §§1.3–1.4 (BDR/ação/FII cisão ex-prices), §5.1 (incorporação com bônus) |
| **Cisão de FII** | FII splits; quota holders may receive quotas of a new FII (or not adhere) | + quotas of new FII if adherence | B3: `P_ex = P_com − q × FII_novo`; if the investor can opt out, no price change | B3 Manual §1.4 |
| **Amortização de quotas (FII)** | Partial return of capital by a fund, quota count unchanged | unchanged | RFB has **no FII-specific amortização clause** in IN 1.585 arts. 35–40 (only "alienação ou resgate de cotas" at 20%); secondary sources say amortização is taxed as ganho de capital at 20% — **verify** (flag) | IN 1.585 art. 37 caput; secondary only (fiis.com.br, investfiis) |
| **Alteração de código de negociação** (ticker change) | Same security, new ticker (e.g., renames, segment migration) | unchanged | No cost/avg effect; **asset identity should key on ISIN** (`isinCode`/`baseIsinCode` exist in B3's position/movement API models) | B3 API models (prior research); practical guidance |
| **Dividendos, JCP, rendimentos** | Cash proventos (already in the `Income` domain) | unchanged | Not corporate-action position math; B3 prices them as cash events | B3 Manual §1.1 |

Notes:
- The B3 manual was **renamed from "Manual de Eventos Complexos" to "Manual de Precipicação de Eventos Corporativos" in v3 (15/09/2022)** (change log, p.26) — it is the current umbrella document for event pricing, not just exotic events.
- B3's investor-relations-company article (27/07/2026) states the operational reality: *"Em muitos casos, a B3 precisa ajustar o preço dos ativos e, eventualmente, as posições dos investidores."* It also classifies events as **involuntários** (dividendos, reorganização ativos — no investor action) vs **voluntários** (subscrição, conversão — require decision/extra money).
- **FIIs/ETFs** use the same quantity math as ações for split/grupamento/bonificação (quota events); FII-specific events are incorporação/cisão/amortização. ETFs are funds and follow fund-quota mechanics; the B3 manual covers "ação ou FII" explicitly and the generic §1.2 covers grupamento/desdobramento/bonificação in assets — ETF treatment is an inference, flagged for verification.

## 2. Where these events appear in B3 data (Área do Investidor and APIs)

### 2.1 The Negociação export — no events (verified again 2026-09-16)

The repo sample `references/movimentacoes_B3.xlsx` (sheet `Negociação`, 82 rows, 06–11/08/2026) contains **only `Compra` (59) / `Venda` (23)**; a keyword scan for `evento`, `desdob`, `grupam`, `bonif`, `incorp`, `cisao`, `fração` finds nothing. Consistent with the prior sibling research (`docs/research/2026-09-15-b3-extract.md`): this export is trades only — it can never carry a corporate action.

### 2.2 The Movimentação dataset — documented to include events

- **B3's own product page** lists the dataset as *"Movimentações (aplicações, **eventos** e vencimentos)"* (b3.com.br Área do Investidor page; recorded verbatim in `docs/research/2026-09-15-b3-area-do-investidor.md`).
- **B3's API manual (Movimentação API)** states the types it plans to cover: *"Movimentação de custódia (transferência de ativos, liquidação de compra e venda), **Eventos Corporativos (pagamento de dividendo, grupamento, desdobramento)**, Empréstimos de ativos (abertura de contrato, liquidação de contrato e reembolso de evento)."* The `EquitiesMovement` model carries `movementType`, `operationType` (`Débito`/`Crédito`), `tickerSymbol`, `equitiesQuantity`, `unitPrice`, `operationValue`, **`movementTypeDetailCode`**, `isinCode`, **`baseIsinCode`**, `processNumber` — i.e., events are modelled as debit/credit quantity rows with an event-type code and a process number. (Swagger fetched 2026-09-15, quoted in the sibling research; my re-fetch attempt on 2026-09-16 was denied by the portal's CSRF token.)
- **Secondary guide (Cota B3, tutorial updated 28/06/2026):** the XLSX "movimentações" download has columns `Data`, `Tipo de Movimentação` (*"Compra, Venda, Transferência, **Grupamento, Desdobramento**, etc."*), `Código de Negociação`, `Instituição`, `Quantidade`, `Preço Unitário`, `Valor`; it claims "eventos corporativos (splits, grupamentos) e transferências" are included, history from 2019, **12 months max per export**. Mark as **secondary** — its column list is the only public schema description and may not match the real file.
- **Money-side events are in a separate API dataset:** Eventos Provisionados exposes `corporateActionTypeDescription` values **`DIVIDENDO`, `RESTITUIÇÃO DE CAPITAL`, `BONIFICAÇÃO EM DINHEIRO`, `JUROS SOBRE CAPITAL PRÓPRIO`, `RENDIMENTO`, `JUROS`, `AMORTIZAÇÃO`, `PRÊMIO`, `ATUALIZAÇÃO MONETÁRIA`, `BONIFICAÇÃO EM ATIVOS`**, with `eventValue` (money **or factor**), `eventQuantity`, `grossAmount`, `netValue`, `incomeTaxPercent/Amount`, `paymentDate`, `specialExDate`, `approvalDate`, `tickerSymbol`, `isin` (swagger fetched 2026-09-15; same source).

### 2.3 Must be verified in-account (no public documentation)

1. Exact `Tipo de Movimentação` value domain of the Movimentação XLSX: are `Transferência`, `Grupamento`, `Desdobramento`, `Bonificação`, `Cisão`, `Incorporação`, `Amortização` all present? Any other code?
2. How an event row encodes its **ratio/factor and resulting quantity** — is `Quantidade` the delta credited/debited, the final position, or empty? Is there a "quantidade anterior"/"fator" pair? Only the 2026 Q&A/vendor guides hint at this; nothing official.
3. Whether FII/fund events (amortização, cisão, incorporação de FII) appear in the same Movimentação file, and how they are labelled.
4. Whether **fractions** from events appear (as rows, or only the auction sale cash row).
5. Maximum selectable range and whether event rows appear for ranges predating the position (2019+ per secondary source).
6. Whether the Movimentação download exposes a stable id (`processNumber` is in the API model; the XLSX schema is unknown).

## 3. How a personal tracker should adjust (brief, factual)

Given a position `(qty, totalCost)` with `avg = totalCost/qty`:

| Event | Apply to quantity | Apply to total cost | Resulting avg |
|---|---|---|---|
| Desdobramento ratio `Q` | `qty × Q` (theoretical); actual credited = floor, fraction → cash | unchanged (added shares cost zero) | `avg / Q` |
| Grupamento ratio `Q` | `qty / Q` (floor); fraction → cash | unchanged | `avg × Q` |
| Bonificação em ações factor `B` and declared value `V` (if any) | `qty × (1+B)` | `totalCost += V` if declared/known; otherwise unchanged (market practice) | `(totalCost + V) / (qty × (1+B))` |
| Bonificação em dinheiro `X` | unchanged | unchanged per sources found (flag: no RFB PF rule found) | unchanged; record cash separately |
| Restituição de capital `X` | unchanged | `totalCost −= X` | `(totalCost − X) / qty` (check ≥ 0; excess handling unverified) |
| Subscrição exercised (`q` new shares @ `K`) | `qty += q` | `totalCost += q × K` (+ cost of rights if bought) | recompute |
| Incorporação / fusão / cisão | replace/add per assembly ratio (`IN 1.585 art. 58 §6` proportion) | carry `totalCost` proportionally to the new shares; cash parcel = partial alienation | recompute per received quantities |
| FII cisão | add quotas per event terms | carry cost proportionally | recompute |
| FII amortização | unchanged | secondary: taxed as ganho de capital at 20% on the excess over cost; cost reduction treatment **unverified** | flag |
| Ticker change | unchanged | unchanged | remap identity (key on ISIN) |

Two invariants to encode as executable checks:
- **Asset events preserve total cost** (split/grupamento/bonificação-em-ações-without-declared-value): `qty_before × avg_before == qty_after × avg_after` within a tolerance equal to the auctioned fraction's cost.
- **Cash events never change quantity**; restituição reduces cost by exactly the cash received.

## 4. Modeling sketch (for the later decision, not a spec)

**Why first-class:** corporate actions are the *only* documented way a listed position's quantity changes without a trade or transfer; the current domain model covers cash proventos (`Income`) but a quantity mutation is neither an `Income` nor a trade. It needs its own ledger entry type with provenance.

Suggested entity (sketch):

```
CorporateAction {
  id
  assetId                 // canonical; ISIN when known, ticker as fallback
  kind: enum {
    DESDOBRAMENTO, GRUPAMENTO, BONIFICACAO_ACOES, BONIFICACAO_DINHEIRO,
    RESTITUICAO_CAPITAL, SUBSCRICAO, INCORPORACAO, FUSAO, CISSAO,
    AMORTIZACAO, TICKER_CHANGE, TRANSFERENCIA, OTHER
  }
  processNumber?          // B3 event/process id, when available (API model has it)
  approvalDate?, exDate?, paymentDate?, appliedAt
  ratio?: Decimal         // new-per-old factor Q or (1+B) for asset events
  cashAmount?: Decimal    // money events
  subscriptionPrice?: Decimal
  quantityEffect?: { factor?: Decimal, delta?: Decimal, resultingQty?: Decimal }
  costEffect: enum { TOTAL_UNCHANGED, ADD_DECLARED_VALUE, REDUCE_BY_CASH, CARRY_OVER }
  source: enum { B3_MOVIMENTACAO, B3_EVENTOS_PROVISIONADOS, BROKER_NOTE, MANUAL }
  sourceRef?: string      // file + row / external id
  status: PENDING | APPLIED | IGNORED | NEEDS_REVIEW
  incomeRef?              // link when the event also produces a cash movement
}
```

Open points the later ticket should decide (do not pre-decide here):
- **Cash/`Income` overlap:** bonificação em dinheiro, restituição de capital and FII amortização are cash events. Restituição is not income (it reduces cost, §8); amortização's nature is disputed (secondary: ganho de capital); bonificação em dinheiro has no PF rule found. Options: (a) `CorporateAction` owns all position+cash-of-event mutations with a `cashTreatment` field; (b) cash-side lives in the existing `Income`/movements ledger and `CorporateAction` is position-only with a link. Pick one home to avoid double-counting.
- **Theoretical vs actual quantities:** store both (e.g., 7% bonus on 133 shares → 9.31 theoretical, 9 credited + fraction auctioned). The custody position is the source of truth; the event record is the explanation.
- **Ratio direction convention:** fix one (e.g., `new per old`) and document it; split 1:10 vs grupamento 10:1 are then unambiguous.
- **Idempotency/provenance:** an event re-imported from the Movimentação export must be dedupable — natural key `(source, processNumber|date, assetId, kind)`; no id is documented in the XLSX.
- **Ordering:** B3 prices multiple same-day events recursively in the order declared by the issuer (Manual §1.5). If the tracker applies several events to one asset on one ex-date, it needs a deterministic application order (store version/ordinal).

## 5. Failure modes

| # | Failure mode | Evidence | Handling |
|---|---|---|---|
| 1 | **Replay from Negociação only is silently wrong after any event** | Negociação sample has no event rows (re-verified 2026-09-16) | Ledger replay must overlay corporate actions; reconciliation check vs custody snapshot |
| 2 | **Unknown `Tipo de Movimentação` values in the Movimentação XLSX** | Only secondary documentation; API lists a subset of event types | Route unknown types to a review queue, never drop (carried from prior research) |
| 3 | **Fractions from split/grupamento/bonificação** | MGLU3 grupamento 10:1 edital: fractions were sold via an "oferta de venda" in the closing call (10/06/2024, 145,676 shares, Itaú CV) — quoted: *"A corretora informou que se trata de frações remanescentes do processo de grupamento"* | Never assume integer quantities: compute theoretical, expect a fraction auction cash row; reconcile `floor` vs credited |
| 4 | **Fractional-market trades** (`F` suffix, e.g., `B3SA3F`) are executions of the same asset | Sample: 5 dual-market assets; prior research | Normalize to underlying ticker; keep raw code + venue |
| 5 | **Multiple events on the same ex-date** | Manual §1.5: B3 applies them recursively in the issuer-declared order | Store event order; apply deterministically; validate against custody |
| 6 | **Snapshot seeding gap** — positions seeded from a Relatório Consolidado snapshot + trade replay miss events between snapshot and today | Prior research seed path; B3 events article: B3 "eventualmente" adjusts positions | Reconciliation invariant: replay result must equal the next custody snapshot per asset; mismatch ⇒ suspect unapplied event |
| 7 | **FII-specific events** (amortização, cisão, incorporação de fundos) | B3 Manual §§1.4 (cisão FII); no FII amortização rule in IN 1.585 arts. 35–40 | Model as `kind` + event-specific terms; verify presence/labels in Movimentação in-account |
| 8 | **Ticker changes** split one asset's history | B3 API models carry `isinCode`/`baseIsinCode` (prior research) | Key asset identity on ISIN when available; treat ticker change as identity remap, not a position event |
| 9 | **Incorporation paying bonus in another company's shares** (e.g., incorporação com bônus) | B3 Manual §5.1 (`bônus = P_mãe − P_filha`) | The event row(s) may reference a different ticker; model as multi-asset event or two linked one-asset effects |
| 10 | **Cost-basis over/under-statement after bonificação** | IN 1.585 §1 says add capitalized value; market practice often just dilutes | Store whether a declared value was available; flag positions where `totalCost` was not adjusted by a declared value |
| 11 | **FII gains have no R$ 20k monthly exemption** | IN 1.585 art. 59 I (ações only); FII at 20% (art. 37) | Cost-basis math must not absorb the ações-only exemption assumption; out of scope for the event model but affects sale simulations |

## 6. Sources

**Primary — B3:**
1. B3 — *Manual de Precipicação de Eventos Corporativos*, v13, 31/07/2024 (public PDF; §1.1 cash events, §1.2 grupamento/desdobramento/bonificação eqs. 1.2–1.3, §1.3 BDR cisão, §1.4 ação/FII cisão, §5.1 incorporação com bônus, change log): <https://b3.com.br/data/files/21/C7/1F/AB/D5A01910812F0F09AC094EA8/Manual%20de%20Eventos%20Complexos_V13.pdf> — downloaded and text-extracted 2026-09-16.
2. B3 (clientes) — "Eventos corporativos exigem comunicação clara e rapidez…", 27/07/2026 (involuntary vs voluntary; B3 adjusts prices and investor positions): <https://clientes.b3.com.br/w/eventoscorporativos> — fetched 2026-09-16.
3. B3 — Consulta "Dividendos e outros eventos corporativos" (event consultation UI; JS-rendered, not extractable here): <https://www.b3.com.br/pt_br/produtos-e-servicos/negociacao/renda-variavel/acoes/consultas/dividendos-e-outros-eventos-corporativos/> — fetched 2026-09-16 (navigation only).
4. B3 — MGLU3 grupamento edital (fractions auction evidence): <https://www.b3.com.br/data/files/9F/05/33/DC/EFEEF8105391B9F8AC094EA8/Edital%20-%20%20MGLU3%20_114_%2010062024%20-%20Grupamento.pdf> — downloaded and text-extracted 2026-09-16.
5. B3 — Área do Investidor product page ("Movimentações (aplicações, eventos e vencimentos)"): <https://www.b3.com.br/pt_br/produtos-e-servicos/central-depositaria/canal-com-investidores/area-do-investidor/> — quoted in `docs/research/2026-09-15-b3-area-do-investidor.md` (fetched 2026-09-15).
6. B3 — Manual Técnico APIs Área do Investidor (Movimentação types incl. "Eventos Corporativos (pagamento de dividendo, grupamento, desdobramento)"; Eventos Provisionados domain) and swagger models (`EquitiesMovement`, `ProvisionedEvents`): quoted in `docs/research/2026-09-15-b3-extract.md` and `…-b3-area-do-investidor.md` (fetched 2026-09-15; re-fetch on 2026-09-16 denied — CSRF token).
7. B3 For Developers — API Area do Investidor (B2B disclaimer): <https://developers.b3.com.br/apis/api-area-do-investidor>.

**Primary — Receita Federal / legislação:**
8. IN RFB nº 1.585/2015, DOU 02/09/2015 seção 1 p.37 (art. 37 FII 20%; arts. 35–40 FII; art. 58 caput avg cost, §1–2 bonificação cost, §6 incorporação/fusão/cisão cost carry-over, §7 II desdobramento cost zero, §8 restituição reduces cost; art. 59 I R$20k exemption): DOU PDF pages 37–45, downloaded and text-extracted 2026-09-16 via Imprensa Nacional (`pesquisa.in.gov.br`); current consolidated view: <https://normasinternet2.receita.fazenda.gov.br/#/consulta/externa/67494/visao/multivigente> (JS SPA; text confirmed there 2026-09-16).
9. RFB — *Perguntas e Respostas IRPF 2026* (Q721 custo de bonificações; Q722 custo de ações desdobradas — zero; Q603 incorporação de ações as alienation): <https://www.gov.br/receitafederal/pt-br/centrais-de-conteudo/publicacoes/perguntas-e-respostas/dirpf/p-r-irpf-2026-v1-00-2026-04-23.pdf/view> — downloaded and text-extracted 2026-09-16.
10. Lei nº 9.249/1995, art. 10, parágrafo único (cost of shares distributed by capitalizing lucros/reservas = capitalized amount): <https://www.planalto.gov.br/ccivil_03/leis/l9249.htm> — fetched 2026-09-16.

**Secondary (cited as such):**
11. Cota B3 — "Como exportar o extrato de movimentações da B3" (updated 28/06/2026): Movimentação XLSX columns; `Tipo de Movimentação` incl. Grupamento/Desdobramento/Transferência; 12-month limit; history from 2019: <https://www.cotab3.com.br/artigos/como-exportar-extrato-movimentacoes-b3> — fetched 2026-09-16.
12. fiis.com.br / investfiis.com.br — FII amortização treated as ganho de capital (20%): <https://fiis.com.br/artigos/amortizacao-de-fundos-imobiliarios/> and <https://investfiis.com.br/blog/fiis-ir-amortizacao-como-declarar-dirpf/> — fetched 2026-09-16 (search snippets; not independently verified against a primary FII-specific clause).

**In-repo:** `references/movimentacoes_B3.xlsx` (git-ignored; re-parsed 2026-09-16 with openpyxl — 82 rows, Compra/Venda only, no event traces); sibling research `docs/research/2026-09-15-b3-area-do-investidor.md`, `docs/research/2026-09-15-b3-extract.md`.

## Open uncertainty

- Exact Movimentação XLSX schema and `Tipo de Movimentação` domain; how event rows carry ratio/quantity; whether fractions and FII events appear (§2.3 checklist).
- Fiscal treatment of **bonificação em dinheiro** for PF cost basis — no RFB rule found in the 2026 Q&A; the tracker should not invent one.
- FII **amortização** tax nature and cost treatment — only secondary sources read; IN 1.585 has no FII-specific amortização clause.
- Whether the API's Eventos Provisionados/`movementTypeDetailCode` enumerations are obtainable for a personal account (APIs are B2B-only; the export path is the fallback).
- ETF-specific event handling (fund-quota mechanics inferred from the FII/asset formulas; not explicitly documented for ETFs).
