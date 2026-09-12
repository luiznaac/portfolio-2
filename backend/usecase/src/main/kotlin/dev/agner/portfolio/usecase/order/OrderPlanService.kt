package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.allocation.AllocationService
import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.attribution.model.AttributionSummary
import dev.agner.portfolio.usecase.commons.isZero
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.model.Quote
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.model.Order
import dev.agner.portfolio.usecase.order.model.OrderKind
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.SaleCeiling
import dev.agner.portfolio.usecase.order.model.StrategyDelta
import dev.agner.portfolio.usecase.order.model.TransferSuggestion
import dev.agner.portfolio.usecase.strategy.StrategyEditionService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.repository.IStrategyWeightRepository
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Clock

/**
 * Assembles the month's executable order list: per strategy per ticker, ideal quantity (from the
 * strategy's own share of its [dev.agner.portfolio.usecase.allocation.model.AssetClass]'s ideal
 * capital — see [dev.agner.portfolio.usecase.strategy.model.StrategyWeight] — times its latest
 * [dev.agner.portfolio.usecase.strategy.model.StrategyEdition]'s target weight) against current
 * attributed quantity, netted per ticker across strategies into one order, with same-ticker
 * excess/shortage matched into [TransferSuggestion]s first. See the plan's "Fases" §3.
 *
 * No I/O of its own: every price, balance and target comes from a repository or gateway injected
 * here, and the pure matching lives in [TransferMatcher]. This class is the orchestration around
 * them (see "Complex calculations get their own class" in `backend/AGENTS.md`).
 *
 * ### How a ticker becomes an order
 *
 * ```mermaid
 * flowchart TD
 *     A["ticker is held, or appears in some strategy's latest edition"] --> B["custody from the trade
 * ledger<br/>attributed balances per strategy"]
 *     B --> C["ideal shares per strategy:<br/>ceil(idealCapital × targetWeight ÷ price)"]
 *     C --> D["delta = attributed − ideal"]
 *     D --> E{"any delta ≠ 0?"}
 *     E -- yes --> F["TransferMatcher: move same-ticker excess<br/>to whoever is short, for free"]
 *     E -- no --> G
 *     F --> G{"custody − Σ ideals<br/>≠ 0 and a price exists?"}
 *     G -- no --> H["no order for this ticker"]
 *     G -- yes --> I["one net order:<br/>FULL_EXIT / NEW_ENTRY / SELL / BUY"]
 *     I --> J["day-trade flag from today's ledger"]
 * ```
 *
 * ### Two things that are deliberately *not* here
 *
 * - **No rateio of unattributed custody.** `Δ balances == custody` is an invariant of the
 *   attribution view, but the plan never assumes it holds: whatever is not attributed to a
 *   strategy simply has no delta and therefore lands in the net order as a real trade. Splitting
 *   it across strategies is the user's decision, never derived — see the plan's "atribuição por
 *   estratégia" decision and [dev.agner.portfolio.usecase.attribution.AttributionSummary].
 * - **Transfers do not shrink the order.** They are a side channel of suggestions; the *net*
 *   quantity stays a function of custody against the sum of ideals, because attribution movements
 *   do not change how many shares the user actually owns.
 */
@Service
class OrderPlanService(
    private val strategyService: StrategyService,
    private val strategyWeightRepository: IStrategyWeightRepository,
    private val strategyEditionService: StrategyEditionService,
    private val allocationService: AllocationService,
    private val attributionService: AttributionService,
    private val listedAssetRepository: IListedAssetRepository,
    private val tradeRepository: ITradeRepository,
    private val quoteGateway: IQuoteGateway,
    private val transferMatcher: TransferMatcher,
    private val clock: Clock,
) {

    suspend fun computePlan(): OrderPlan {
        val today = LocalDate.today(clock)
        val strategies = strategyService.fetchAll()

        // The whole month's plan hangs off three snapshots taken once here: where the strategies
        // are weighted today, what each one wants its tickers to be, and the current allocation
        // capital that turns a weight into an actual share count.
        val strategyById = strategies.associateBy { it.id }
        val strategyNames = strategies.associate { it.id to it.name }
        val weightByStrategy = strategyWeightRepository.fetchCurrent(today).associate { it.strategyId to it.weight }
        val classIdealByClass = allocationService.currentPlan().classes.associate { it.assetClass to it.ideal }
        val idealCapitalByStrategy = strategies.associate { strategy ->
            val strategyWeight = weightByStrategy[strategy.id] ?: BigDecimal.ZERO
            val classIdeal = classIdealByClass[strategy.assetClass] ?: BigDecimal.ZERO
            strategy.id to (strategyWeight * classIdeal)
        }
        val targetsByStrategy = latestTargetsByStrategy(strategies.map { it.id })
        val targetedTickers = targetsByStrategy.values.flatMapTo(mutableSetOf()) { it.keys }

        val listedAssets = listedAssetRepository.fetchAll()
        val assetsByTicker = listedAssets.associateBy { it.ticker }

        // A targeted ticker with no registered ListedAsset yet (e.g. a new entrant not imported into
        // the app) cannot be planned for: it has no id to hang an order off, and no price to value
        // it with. Left as a known gap rather than silently inventing an asset.
        val tickersToInspect = targetedTickers + listedAssets.map { it.ticker }

        val orders = mutableListOf<Order>()
        val transferSuggestions = mutableListOf<TransferSuggestion>()

        for (ticker in tickersToInspect) {
            val asset = assetsByTicker[ticker] ?: continue

            // Fetched once per asset and reused for the relevance check, the deltas and the order
            // below — `summarize` replays the asset's trades and corporate actions, so it is the
            // most expensive call in this loop. Closed positions that nothing targets any more are
            // dropped here, before any of the per-asset work below.
            val summary = attributionService.summarize(asset.id)
            val isTargeted = ticker in targetedTickers
            if (summary.custodyQuantity.isZero() && !isTargeted) continue

            val quote = quoteGateway.getQuote(asset)
            val idealByStrategy = idealQuantitiesByStrategy(
                strategyIds = strategyById.keys,
                targetWeights = targetsByStrategy,
                idealCapitalByStrategy = idealCapitalByStrategy,
                ticker = ticker,
                price = quote?.price,
            )
            val deltaByStrategy = deltas(
                currentByStrategy = summary.balances.associate { it.strategyId to it.quantity },
                idealByStrategy = idealByStrategy,
            )

            if (deltaByStrategy.values.any { !it.isZero() }) {
                transferSuggestions += transferMatcher.match(asset.id, asset.ticker, deltaByStrategy)
            }

            val order = orderFor(
                asset = asset,
                summary = summary,
                quote = quote,
                idealByStrategy = idealByStrategy,
                deltaByStrategy = deltaByStrategy,
                strategyNames = strategyNames,
                today = today,
            )
            if (order != null) orders += order
        }

        return OrderPlan(
            orders = orders,
            transferSuggestions = transferSuggestions,
            saleCeiling = saleCeiling(today, listedAssets, orders),
        )
    }

    /**
     * Latest edition's target weights per strategy, keyed by ticker for O(1) lookup while walking
     * the assets. An empty edition list (or no edition at all) means "wants nothing" — that is
     * `BigDecimal.ZERO` everywhere downstream, which is what makes a fully-exited ticker produce a
     * [OrderKind.FULL_EXIT] instead of silently disappearing.
     *
     * `fetchEditions` throws [dev.agner.portfolio.usecase.strategy.StrategyNotFoundException] for an
     * unknown strategy, which cannot happen here: the ids come from `StrategyService.fetchAll()`.
     */
    private suspend fun latestTargetsByStrategy(strategyIds: Collection<Int>): Map<Int, Map<String, BigDecimal>> =
        strategyIds.associateWith { strategyId ->
            strategyEditionService.fetchEditions(strategyId)
                .lastOrNull()
                ?.edition
                ?.targets
                .orEmpty()
                .associate { it.ticker to it.weight }
        }

    /**
     * Ideal share count per strategy for one ticker, keyed by the strategy ids that are *involved*
     * with it (holding it now, or targeting it).
     *
     * **Rounding is deliberately asymmetric and downward.** `divide(…, 0, DOWN)` floors to whole
     * shares, so a strategy's ideal never exceeds what its capital actually buys; the plan is a
     * buy/sell list of whole shares, and rounding up would propose spending money the strategy does
     * not have. The leftovers accumulate in the net order, which is the safe direction: it can only
     * ever propose selling slightly more or buying slightly less than the theoretical exact target.
     *
     * A missing price (or a non-positive one) collapses every ideal to zero, which is the honest
     * answer for "cannot be valued right now" — the asset then shows up as a full exit rather than
     * being quietly skipped. A zero target weight short-circuits the same way.
     */
    private fun idealQuantitiesByStrategy(
        strategyIds: Collection<Int>,
        targetWeights: Map<Int, Map<String, BigDecimal>>,
        idealCapitalByStrategy: Map<Int, BigDecimal>,
        ticker: String,
        price: BigDecimal?,
    ): Map<Int, BigDecimal> = strategyIds.associateWith { strategyId ->
        val targetWeight = targetWeights[strategyId]?.get(ticker) ?: BigDecimal.ZERO
        val idealCapital = idealCapitalByStrategy[strategyId] ?: BigDecimal.ZERO
        val isQuotable = price != null && price > BigDecimal.ZERO

        if (!isQuotable || targetWeight.isZero()) {
            BigDecimal.ZERO
        } else {
            (idealCapital * targetWeight).divide(price, 0, RoundingMode.DOWN)
        }
    }

    /**
     * `current − ideal`, in shares, for every strategy involved with the ticker. Positive means
     * holding more than the target calls for (excess), negative means short.
     *
     * Strategies that exist only on one side of the map are handled by defaulting the missing side
     * to zero, so a strategy that holds the ticker without targeting it shows up as pure excess
     * (candidate to hand over, then to sell) and a strategy that targets it without holding it
     * shows up as pure shortage (candidate to receive, then to buy).
     *
     * Zero deltas are kept rather than filtered out — [Order.contributions] is documented as
     * informational, and the caller is the one that decides whether a ticker is worth a transfer
     * suggestion at all.
     */
    private fun deltas(
        currentByStrategy: Map<Int, BigDecimal>,
        idealByStrategy: Map<Int, BigDecimal>,
    ): Map<Int, BigDecimal> =
        (currentByStrategy.keys + idealByStrategy.keys).associateWith { strategyId ->
            (currentByStrategy[strategyId] ?: BigDecimal.ZERO) - (idealByStrategy[strategyId] ?: BigDecimal.ZERO)
        }

    /**
     * The single net order for one ticker, or null when nothing has to trade — either because
     * custody already matches the sum of the ideals, or because there is no price to trade at.
     *
     * The quantity is `custody − Σ ideals` regardless of how the deltas are distributed: attribution
     * only decides who *should* own the shares, not how many the user owns, so it cannot change the
     * size of the trade. That is also why the transfer suggestions above never reduce this number.
     *
     * Ordering of the `when` matters: the first two branches describe what custody *currently* is
     * relative to every strategy's wish, and only the last two look at the sign of the net delta.
     * A ticker with custody and no remaining target anywhere is a full exit ([OrderKind.FULL_EXIT]), not
     * a partial one; one with a target and no custody is a fresh entry ([OrderKind.NEW_ENTRY]).
     * `wantsSome` is the shared half of both tests, and it is exactly "not every ideal is zero".
     */
    private suspend fun orderFor(
        asset: ListedAsset,
        summary: AttributionSummary,
        quote: Quote?,
        idealByStrategy: Map<Int, BigDecimal>,
        deltaByStrategy: Map<Int, BigDecimal>,
        strategyNames: Map<Int, String>,
        today: LocalDate,
    ): Order? {
        val netDelta = summary.custodyQuantity - idealByStrategy.values.sumOf { it }
        if (netDelta.isZero() || quote == null) return null

        val wantsSome = idealByStrategy.values.any { it > BigDecimal.ZERO }
        val kind = when {
            !wantsSome && summary.custodyQuantity > BigDecimal.ZERO -> OrderKind.FULL_EXIT
            summary.custodyQuantity.isZero() && wantsSome -> OrderKind.NEW_ENTRY
            netDelta > BigDecimal.ZERO -> OrderKind.SELL
            else -> OrderKind.BUY
        }
        val quantity = netDelta.abs()

        return Order(
            listedAssetId = asset.id,
            ticker = asset.ticker,
            isFii = asset.kind == AssetKind.FII,
            kind = kind,
            quantity = quantity,
            notional = (quantity * quote.price).setScale(2, RoundingMode.HALF_EVEN),
            contributions = deltaByStrategy
                .filterValues { !it.isZero() }
                .map { (strategyId, delta) ->
                    StrategyDelta(
                        strategyId = strategyId,
                        strategyName = strategyNames[strategyId].orEmpty(),
                        delta = delta,
                    )
                },
            dayTradeRisk = hasOppositeTradeToday(
                assetId = asset.id,
                today = today,
                sell = kind == OrderKind.SELL || kind == OrderKind.FULL_EXIT,
            ),
        )
    }

    /**
     * A trade on the same ticker, today, pointing the other way is what turns this order into a day
     * trade. Read straight from the ledger rather than from the consolidation view, so it reflects
     * whatever the user has already reported, not the plan's own suggestions.
     *
     * This flags, it never blocks — see [Order.dayTradeRisk] and the plan's "Fases" §3.
     */
    private suspend fun hasOppositeTradeToday(assetId: Int, today: LocalDate, sell: Boolean): Boolean =
        tradeRepository.fetchByAssetId(assetId).any { trade ->
            trade.date == today && if (sell) trade.quantity > BigDecimal.ZERO else trade.quantity < BigDecimal.ZERO
        }

    /**
     * How much of this month's R$20,000 stock-sale tax exemption is already used up, counting both
     * what was actually sold in the ledger since the 1st and what this plan would sell if executed
     * whole — so the meter answers "if I do all of this, where do I land?" rather than "where am I
     * now?".
     *
     * **FIIs are excluded on purpose.** They have no exemption at all and are always taxed, so
     * counting them here would understate how much of the ceiling the stocks have really eaten. The
     * ledger side is filtered by the asset's registered [AssetKind.STOCK] for the same reason, which
     * means a sale of an asset that is not registered lands on neither side of the meter — a known
     * blind spot of the same family as the unregistered-ticker gap in [computePlan].
     *
     * `remaining` floors at zero so a blown ceiling reads as "nothing left" instead of a negative
     * allowance; whether the ceiling is actually blown is derived by the consumer from
     * `monthSold > limit`. The limit is fixed at R$20k because that is the statutory monthly
     * ceiling in force for Brazilian stocks, not a tunable.
     */
    private suspend fun saleCeiling(
        today: LocalDate,
        listedAssets: List<ListedAsset>,
        orders: List<Order>,
    ): SaleCeiling {
        val monthStart = LocalDate(today.year, today.month, 1)
        val kindByAssetId = listedAssets.associate { it.id to it.kind }

        val settledStockSales = tradeRepository.fetchAll()
            .filter { it.date in monthStart..today && it.quantity < BigDecimal.ZERO }
            .filter { kindByAssetId[it.assetId] == AssetKind.STOCK }
            .sumOf { it.quantity.abs() * it.price }

        val plannedStockSales = orders
            .filter { !it.isFii && (it.kind == OrderKind.SELL || it.kind == OrderKind.FULL_EXIT) }
            .sumOf { it.notional }

        val monthSold = settledStockSales + plannedStockSales
        val limit = BigDecimal("20000.00")
        val remaining = (limit - monthSold).max(BigDecimal.ZERO)

        return SaleCeiling(monthSold = monthSold, limit = limit, remaining = remaining)
    }
}
