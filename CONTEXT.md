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

**Aporte**:
New money added to the Portfolio from outside during a month; part of Capital.
_UI:_ "Aporte"
_Avoid_: Deposit (that is an RF movement), contribution

**Target**:
A percentage a level of the chain is managed against: class, strategy or sub-class share, or ticker weight.
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
A proposed trade the engine generates to close a deficit — kinds `BUY`, `SELL`, `EXIT` (full position out), `NEW_ENTRY` — with a lifecycle from suggested to executed (derived by matching imported trades, overridable) to imported.
_UI:_ "Ordem"
_Avoid_: Trade (that is the executed record), order plan

**Attribution Transfer**:
Moving part of a listed asset's Attribution from one Strategy to another without touching custody; rides in the orders list as its own kind.
_UI:_ "Transferência"
_Avoid_: Custody transfer (that is between institutions, ticketed separately), reallocation

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

### Listed assets

**Strategy**:
A named bucket of the user's Listed-Assets money driven by an external report (the five XP strategies); carries a Target share within its Asset Class.
_UI:_ "Estratégia"
_Avoid_: Report (that is the Edition's source), carteira

**Edition**:
One monthly instance of a Strategy: the parsed report for a month, holding per-ticker weights; staged as a diff against the previous Edition before it goes active. One active Edition per Strategy; history kept; a ticker leaving a report means weight 0, not deletion.
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
The instrument type of a listed asset: `STOCK`, `UNIT`, `FII`, `BDR`, `ETF`. Drives tax treatment and UI presentation.
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
The target bucket an RF product allocates to: `PREFIXADO` (fixed rate), `POSFIXADO` (floating on CDI/SELIC), `INFLACAO` (floating on IPCA).
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
