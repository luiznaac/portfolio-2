# Carteira Ideal

Domain language for portfolio-2: a personal app that replaces the `CARTEIRA IDEAL` spreadsheet — a monthly rebalancing ritual (update Capital, compare holdings against targets, generate suggested orders) plus continuous tracking of a Brazilian investment portfolio (positions, proventos, corporate actions, history).

The domain splits into two sub-domains, composed by a planning layer:

- **Fixed Income** — Renda Fixa products valued by accrual (the model the backend already carries).
- **Listed Assets** — Renda Variável instruments traded on B3, tracked by a trade ledger.
- **Portfolio** — the planning layer that composes both into Capital, targets and suggested orders.

Canonical terms are English (they name code concepts); each entry records the pt-BR term the UI renders it as.

## Language

### Portfolio & planning

**Portfolio**:
Everything the user holds across both sub-domains, managed against targets.
_UI:_ "Carteira"
_Avoid_: Wallet, account, holdings

**Capital**:
The manually entered monthly total value of the Portfolio — current balances including appreciation plus planned aportes — that every Target multiplies.
_UI:_ "Capital"
_Avoid_: Patrimônio, total balance, equity

**Month**:
The planning period the ritual runs in — one open month at a time, carrying that month's Capital, cash Floor and Targets.
_UI:_ "Mês"
_Avoid_: Period, cycle

**Aporte**:
New money added to the Portfolio from outside during a month, recorded as a net figure at each Fechamento and frozen into the Snapshot; part of Capital. The performance decomposition nets it out, and XIRR dates it at month-end.
_UI:_ "Aporte"
_Avoid_: Deposit (that is an RF movement), contribution

**Target**:
A percentage a level of the chain is managed against: class, Report Strategy or sub-class share, or ticker weight. Targets belong to a month, never standing config.
_UI:_ "Alvo"
_Avoid_: Goal, weight (that is the edition's per-ticker number)

**Ideal**:
The R$ amount a level should hold: Capital multiplied by the chain of Targets above it.
_UI:_ "Ideal"
_Avoid_: Target amount, alvo em reais

**Deficit**:
How much money a level is short of its Ideal (Ideal minus Actual, positive side).
_UI:_ "Déficit"
_Avoid_: Shortfall, gap

**Surplus**:
How much money a level holds above its Ideal (Ideal minus Actual, negative side).
_UI:_ "Superávit"
_Avoid_: Excess, leftover

**Drift**:
A level's percentage-point deviation from its Target; the UI word for it.
_UI:_ "Drift"
_Avoid_: Deficit (that is in R$), error

**Uninvested Balance**:
Money in the Portfolio not held by any position: computed as Capital minus the value of all positions; never stored.
_UI:_ "Caixa" (residual display only)
_Avoid_: Caixa as a class (not modeled), cash account

**Suggested Order**:
A proposed trade the engine generates to close a deficit — kinds `BUY`, `SELL`, `EXIT` (manual, full position out), `NEW_ENTRY`, and the RF order's `CONTRIBUTE`/`REDEEM` — with a lifecycle from suggested to executed (derived by matching imported trades, overridable) to imported.
_UI:_ "Ordem"
_Avoid_: Trade (that is the executed record), order plan

**RF Order**:
A suggested order at a Renda Fixa sub-class leaf, sized as "contribute R$X" or "redeem R$Y"; the product and its details are chosen manually when the order is executed, not planned ahead.
_UI:_ "Ordem" (RF)
_Avoid_: Product order (no product is named yet)

**NEUTRO**:
The "não operar" flag on an Edition Entry, applied per (Strategy, ticker): the slice generates no BUY and its Deficit stays visible and unfunded; its units are sold only to fund other buys.
_UI:_ "Não operar"
_Avoid_: Neutral (that is the analyst Rating), blocked, inactive

**Funding**:
Selling positions to cover BUYs the Uninvested Balance cannot pay for, in a fixed source order: NEUTRO slices, positions outside every Strategy, then the largest Surpluses by R$. Sells fund buys — never a Surplus on its own.
_UI:_ —
_Avoid_: Rebalancing (that is the whole plan), liquidation

**Attribution Transfer**:
Moving part of a listed asset's Attribution from one Strategy to another without touching custody; rides in the orders list as its own kind. Suggested whenever two slices of a ticker sit ≥ 1 whole unit off their Ideal, or a report slice is short while its custody is satisfied — a Manual Strategy (the bucket) may donate units as a target-less source, but such a suggestion always waits for a human, whatever the R$ amount.
_UI:_ "Transferência"
_Avoid_: Custody transfer (that is between institutions, ticketed separately), reallocation

**Inside a Strategy**:
A held Ticker is inside a Strategy when a Target names it with weight > 0, or when a Manual Strategy's Attribution holds at least one of its units. It is a property of the Ticker, not of a unit; an Attribution to a Report Strategy alone is inert.
_UI:_ —
_Avoid_: Attributed (that is the ledger row), targeted (that is only the Target half)

**Outside Strategy**:
A held, classified Ticker that no Target names and no Manual Strategy holds: the engine holds it and surfaces it for attention, never auto-exits it, and a manual EXIT defaults to the full position. It is a transient state, not a home for legacy money.
_UI:_ "Fora da estratégia"
_Avoid_: Exited (that is the manual act), untracked

**Unclassified Ticker**:
A held Ticker whose effective catalog class is null: it counts in the Portfolio total, belongs to no class or Strategy rung, is never sold by Funding and waits for classification. It is a data-quality state, never an Outside Strategy holding.
_UI:_ "Sem classe"
_Avoid_: Outside strategy (a planning state), unknown ticker

**Pro-rata Scaling**:
The full plan's last resort when funding still cannot cover the buys: every BUY is scaled by the same fraction of its demand, floored to whole units, leaving the remainder unfunded; NEUTRO never participates.
_UI:_ "Pro-rata"
_Avoid_: Partial order (that is a lifecycle state), proportional cut

**Monthly Close**:
Marking a month as done: locks that month's state and persists a Snapshot.
_UI:_ "Fechamento"
_Avoid_: Consolidation (that is the RF engine), settlement

**Snapshot**:
The persisted state of a closed month — holdings, values, attribution — that historical evolution is built from.
_UI:_ "Fechamento" record
_Avoid_: Backup, export

**Institution**:
A broker/custodian named in imported records, normalized into a first-class entity; used for provenance and dedup of imported movements.
_UI:_ "Instituição"
_Avoid_: Broker, corretora, bank

### History & performance

**Backfill**:
Replaying the exported trade history over the whole window it covers, then retiring the adoption seeds it explains, so the ledger reconstructs the Portfolio before adoption. Any quantity the replay still cannot explain becomes a GAP.
_UI:_ "Histórico"
_Avoid_: Import (that stages a file), sync

**Absorb**:
The backfill step that retires an OPENING seed a ticker's imported history now covers — fully (the seed is deleted) or partially (it keeps only the uncovered quantity). Coverage is per ticker.
_UI:_ —
_Avoid_: Merge, reconcile (that is custody vs ledger)

**Opening Trade**:
An adoption seed: one marked Trade built from the B3 Posição snapshot at the start of the tracked window, retired by the backfill once the exported history covers it.
_UI:_ —
_Avoid_: Seed position, initial trade

**GAP**:
A marked reconciliation record for a custody quantity delta the ledger cannot explain: an upward one is a missing buy priced at the market close (so its average price is approximate), a downward one a missing sale whose proceeds are unknown (so its realized result stays `pending`). A GAP is not a Trade — it never feeds the tax ledger; a new trade of the same ticker, side and quantity, or a new quantity Corporate Action, retires it, and either variant's placeholder price can be corrected directly (a missing sale's proceeds make its realized result stop being pending).
_UI:_ "Lacuna"
_Avoid_: Adjustment (that is an RF movement), opening trade

**Performance Decomposition**:
A month's return after the money that is not performance is netted out: `(value_end − value_start − net aportes − net income) ÷ value_start`. The same formula runs for the total, each class and each ticker; `value_start` is the prior Snapshot's value.
_UI:_ "Rentabilidade"
_Avoid_: Yield (that is RF accrual), profit

**XIRR**:
The annualized money-weighted return since inception, solved over the date-month-end snapshot values and the dated net Aportes. It answers what rate makes the contribution and value flows balance.
_UI:_ "Retorno anualizado"
_Avoid_: CAGR (that is time-weighted), monthly return

**Benchmark Blend**:
The comparison line the Portfolio is measured against: each class's benchmark weighted by the month's Target chain — Ações and FIIs against IBOV, RF Pós/Pré against CDI, RF Inflação against IPCA, from the BCB SGS series — never the actual allocation.
_UI:_ "Benchmark"
_Avoid_: Index (the series itself), target

### Listed assets

**Strategy**:
The umbrella for a named bucket of the user's Listed-Assets money; it is either a Report Strategy or a Manual Strategy, told apart by its stored kind.
_UI:_ "Estratégia"
_Avoid_: Report (that is the Edition's source), carteira

**Report Strategy**:
A Strategy fed by an Edition and planned through Targets; its share within its Asset Class lives in the month's Targets, never on the Strategy.
_UI:_ "Com relatório"
_Avoid_: XP strategy, report (that is the Edition's source)

**Manual Strategy**:
The legacy bucket: a Strategy with no Edition and no Targets, at most one per Asset Class. It counts in its class Actual through Attribution, generates no plan orders, is ring-fenced from Funding, and its units leave only through an Attribution Transfer or a manual sell.
_UI:_ "Manual (legado)" / "Legado"
_Avoid_: Legacy Holding (there is no separate entity), synthetic strategy

**Edition**:
One monthly instance of a Report Strategy: the parsed report for a month, holding per-ticker weights; staged as a diff against the previous Edition before it goes active. One active Edition per Report Strategy; history kept; a ticker leaving a report means weight 0, not deletion.
_UI:_ "Edição"
_Avoid_: Report (the PDF artifact), import (the act)

**Edition Entry**:
A line inside an Edition: ticker, weight, rating and target price.
_UI:_ —
_Avoid_: Row, position, target (a Target is a chain percentage)

**Listed Asset**:
An instrument traded on B3 — Ação, unit, FII, BDR, ETF — held through a trade ledger.
_UI:_ "Ativo"
_Avoid_: Security, stock, papel

**Ticker**:
The normalized B3 trading code that identifies a listed asset: uppercase, with the fractional-market `F` suffix stripped (`B3SA3F` resolves to `B3SA3`).
_Avoid_: Código de Negociação (the raw import column), symbol

**Asset Class**:
The allocation bucket a holding rolls up to: `ACOES`, `FIIS`, `RENDA_FIXA`.
_UI:_ "Classe"
_Avoid_: Caixa as a class (not modeled), category, tipo

**Asset Kind**:
The instrument type of a listed asset: `STOCK`, `UNIT`, `FII`, `BDR`, `ETF`, plus `UNKNOWN` for a B3 BDI code the catalog does not classify yet. Drives tax treatment and UI presentation.
_UI:_ "Tipo"
_Avoid_: Asset class (different concept), mercado

**Position**:
The custody state of a listed asset — quantity and average price — regardless of how many strategies it is attributed to.
_UI:_ "Posição"
_Avoid_: Custody position (redundant), balance

**Average Price**:
The fiscal mean unit cost of a listed asset's Position, built from its trades and corporate-action adjustments.
_UI:_ "Preço médio"
_Avoid_: Cost basis, avg cost

**Exemption Meter**:
The month-to-date total of gross STOCK sales measured against the R$20k monthly exemption; crossing it taxes the whole month's stock gain (the cliff), while units, FIIs, BDRs, ETFs and day trades stay outside it.
_UI:_ "Isenção"
_Avoid_: Limit (the value), exemption (the state)

**Realized Result**:
A month's net capital-gain result, per tax kind — swing STOCK, day trade and FII — as the sum of that kind's gains minus its losses, measured against Average Price at each sale.
_UI:_ "Ganho realizado"
_Avoid_: Profit/loss (that is the market view), income

**Carry Bucket**:
A per-kind stock of banked losses that only offsets future gains of the same kind: swing STOCK (unbounded in time), day trade, and FII (same species only); its opening balance is seeded once at adoption.
_UI:_ "Prejuízo a compensar"
_Avoid_: Loss balance, credit

**Day Trade**:
The offsetting of the same ticker at the same institution on the same date, detected as a warning guard and outside the Exemption Meter; the app does not compute day-trade tax.
_UI:_ "Day trade"
_Avoid_: Same-day sale (a sale is not necessarily a day trade)

**Harvest**:
The tax-loss pass after rebalancing: sell a losing STOCK position on day D and rebuy it on the next business day — never the same day — sized to the remaining Exemption Meter headroom, so the loss is realized into its Carry Bucket while the position is kept. A ticker can be opted out; FIIs are report-only because their gains are always taxed and their losses are species-bound.
_UI:_ "Harvest" / "Step-up D+1"
_Avoid_: Day trade (the pair is deliberately D+1), swing trade

**Attribution**:
The ledger mapping parts of a listed asset's Position quantity to Strategies; the sum of attributions never exceeds the Position's quantity.
_UI:_ "Atribuição"
_Avoid_: Allocation (that is the target chain), distribution

**Unattributed**:
The remainder of a Position's quantity mapped to no strategy.
_UI:_ "Não atribuído"
_Avoid_: Residual (that is the Uninvested Balance), orphan

**Trade**:
A buy or a sell of a listed asset: side, quantity, price, date, institution and the raw B3 code.
_UI:_ "Negócio"
_Avoid_: Order (that is a suggested order), movimentação (the umbrella file word)

**Corporate Action**:
An event that changes a listed asset's quantity or cost basis without a trade — desdobramento, grupamento, bonificação, a ticker change, a cash return — imported holder-neutral and applied by the engine; a voluntary or unclassified event waits in the review queue.
_UI:_ "Evento corporativo"
_Avoid_: Provento (that is cash income), trade, movimentação (the umbrella file word)

**Income**:
Money a listed asset pays its holder — dividend, JCP, FII rendimento — fed automatically from exports, tracked through a lifecycle (announced, payable, received, reconciled) with gross, retained and net amounts.
_UI:_ "Provento"
_Avoid_: Yield (that is RF accrual), rendimento (ambiguous), dividend as umbrella

### Imports & reconciliation

**Import**:
A first-class artifact of bringing external data in: source, file, hash, status and parsed rows; staged for review, then applied. Re-importing the same file is detected by its hash.
_UI:_ "Importação"
_Avoid_: Upload, sync, parse (the act)

**Provenance**:
The origin of a record: B3 extract, XP report, Kinvo extrato, or manual entry.
_UI:_ "Origem"
_Avoid_: Source file, metadata

### Fixed income

**Fixed-Income Product**:
An RF instrument the Portfolio holds: kind `BOND` (a direct fixed-income security, "Título") or `ACCOUNT` (a deposit-style product, "Conta" — e.g. PicPay/Kinvo accounts).
_UI:_ "Produto"
_Avoid_: Bond as the umbrella, investment

**Contribution**:
Money into a Fixed-Income Product (aplicação); an engine order.
_UI:_ "Aplicação"
_Avoid_: Deposit, aporte (that is external money), buy

**Redemption**:
Money out of a Fixed-Income Product (resgate), partial or total; an engine order.
_UI:_ "Resgate"
_Avoid_: Withdrawal as the umbrella, sale, sell

**Fixed Income**:
The sub-domain of products whose value accrues from an index or a fixed rate (CDBs, Tesouro Direto, contas remuneradas); a position is money — principal, yield, taxes — never a quantity.
_UI:_ "Renda Fixa"
_Avoid_: Bond as the umbrella term, Caixa

**Fixed-Income Sub-Class**:
The target bucket an RF product allocates to: `FIXED_RATE` ("pré-fixado"), `FLOATING_RATE` ("pós-fixado", floating on CDI/SELIC), `INFLATION_LINKED` ("inflação", floating on IPCA).
_UI:_ "Sub-classe"
_Avoid_: Indexador (that is the rate series itself, not the bucket)

**Consolidation**:
The engine that accrues a fixed-income product's value day by day from its orders and index rates; the source of truth for RF positions.
_UI:_ "Consolidação"
_Avoid_: Settlement, close, update

**Fixed-Income Position**:
An RF product's money state — principal, yield, taxes — computed by Consolidation; products outside the engine carry a manually entered balance instead.
_UI:_ "Posição"
_Avoid_: Balance (that is the manual fallback), saldo
