package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.commons.isWeekend
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.commons.toMondayIfWeekend
import dev.agner.portfolio.usecase.commons.today
import dev.agner.portfolio.usecase.listedasset.AttributionService
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.order.model.tradeSide
import dev.agner.portfolio.usecase.order.repository.ISuggestedOrderRepository
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.tax.model.DEFAULT_HARVEST_MINIMUM_LOSS
import dev.agner.portfolio.usecase.tax.model.HarvestContext
import dev.agner.portfolio.usecase.tax.model.HarvestPairCreation
import dev.agner.portfolio.usecase.tax.model.HarvestReport
import dev.agner.portfolio.usecase.tax.model.TickerAveragePrice
import dev.agner.portfolio.usecase.tax.repository.IHarvestOptOutRepository
import dev.agner.portfolio.usecase.tax.repository.IHarvestPairRepository
import kotlinx.coroutines.awaitAll
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service
import java.math.BigDecimal
import java.time.Clock

// The harvest read and its two writes. The read gathers what the pure pass needs — the open month's
// remaining R$20k headroom and custody after the rebalancing plan's still-unexecuted stock sells,
// the Attribution slices, the NEUTRO flags and the owner's opt-outs — and never stores a suggestion.
// Accepting a pair and toggling an opt-out are the writes; both return the fresh report so the screen
// updates in one round trip.
@Service
class HarvestService(
    private val months: MonthService,
    private val tax: StockTaxService,
    private val catalog: CatalogService,
    private val quotes: QuoteService,
    private val attributions: AttributionService,
    private val strategies: StrategyService,
    private val suggestedOrders: ISuggestedOrderRepository,
    private val optOuts: IHarvestOptOutRepository,
    private val harvestPairs: IHarvestPairRepository,
    private val clock: Clock,
) {

    // Null when no month is open — the edge maps that to the 404 the current-month reads promise.
    suspend fun fetch(): HarvestReport? {
        val current = months.fetchCurrent() ?: return null
        val taxReport = tax.fetchCurrent() ?: return null
        val assetKinds = catalog.fetchAll().associate { it.ticker to it.assetKind }
        val plan = afterRebalancing(
            monthId = current.month.id,
            positions = taxReport.averagePrices,
            assetKinds = assetKinds,
            grossSales = taxReport.meter.grossSales,
        )
        val acceptedPairs = harvestPairs.fetchBy(current.month.id)
        val prices = plan.positions
            .mapAsync { position -> position.ticker to quotes.latestClose(position.ticker) }
            .awaitAll()
            .mapNotNull { (ticker, price) -> price?.let { ticker to it } }
            .toMap()

        val computation = HarvestCalculator.compute(
            HarvestContext(
                // A weekend run plans the sell for the next business day, so both legs stay tradable.
                sellDate = LocalDate.today(clock).toMondayIfWeekend(),
                headroom = plan.headroom,
                positions = plan.positions,
                prices = prices,
                assetKinds = assetKinds,
                slicesByTicker = attributions.fetchSlicesByTicker(),
                neutroSlices = strategies.fetchNeutroSlices(),
                optOuts = optOuts.fetchAll(),
                acceptedTickers = acceptedPairs.map { it.ticker }.toSet(),
            ),
        )

        return HarvestReport(
            period = current.month.period,
            headroom = plan.headroom,
            minimumLoss = DEFAULT_HARVEST_MINIMUM_LOSS,
            suggestions = computation.suggestions,
            ignored = computation.ignored,
            fiiReport = computation.fiiReport,
            pairs = acceptedPairs,
        )
    }

    // Accepting stores the pair with the loss its sell leg realizes at the fiscal average price. The
    // legal invariants are enforced here — the ticker is a STOCK, the rebuy is a later business day,
    // the sell fits its Attribution slice and the post-plan position, and the legs trade at least one
    // unit — while the planner's gate and headroom stay suggestions: the owner may deliberately
    // accept a different pair, and the executed trade is what the meter counts.
    suspend fun accept(creation: HarvestPairCreation): HarvestReport? {
        val current = months.fetchCurrent() ?: return null
        val taxReport = tax.fetchCurrent() ?: return null
        val ticker = creation.ticker.normalizedTicker()
        val assetKinds = catalog.fetchAll().associate { it.ticker to it.assetKind }
        val acceptedPairs = harvestPairs.fetchBy(current.month.id)
        val position = afterRebalancing(
            monthId = current.month.id,
            positions = taxReport.averagePrices,
            assetKinds = assetKinds,
            grossSales = taxReport.meter.grossSales,
        ).positions
            .firstOrNull { it.ticker == ticker }
            ?: throw InvalidHarvestPairException("$ticker has no held position to harvest")

        val kind = assetKinds[ticker]
        if (kind != AssetKind.STOCK) {
            throw InvalidHarvestPairException(
                "$ticker is ${kind ?: "uncatalogued"}; only STOCK positions are harvestable",
            )
        }
        if (acceptedPairs.any { it.ticker == ticker }) {
            throw InvalidHarvestPairException("$ticker already has an accepted pair this month")
        }
        if (ticker in optOuts.fetchAll()) {
            throw InvalidHarvestPairException("$ticker is opted out of harvest")
        }
        if (creation.rebuyDate <= creation.sellDate) {
            throw InvalidHarvestPairException("the rebuy must be on a later day than the sell")
        }
        if (creation.sellDate.isWeekend() || creation.rebuyDate.isWeekend()) {
            throw InvalidHarvestPairException("both legs must fall on a business day")
        }
        if (creation.sellQuantity < BigDecimal.ONE || creation.rebuyQuantity < BigDecimal.ONE) {
            throw InvalidHarvestPairException("both legs must trade at least one unit")
        }
        if (creation.sellQuantity > position.quantity) {
            throw InvalidHarvestPairException(
                "the sell leg trades ${creation.sellQuantity}, but $ticker holds ${position.quantity}",
            )
        }
        if (creation.sellPrice.signum() <= 0 || creation.rebuyPrice.signum() <= 0) {
            throw InvalidHarvestPairException("both leg prices must be positive")
        }

        val loss = position.averagePrice.subtract(creation.sellPrice).multiply(creation.sellQuantity)
        if (loss.signum() <= 0) {
            throw InvalidHarvestPairException(
                "the sell leg realizes no loss at $ticker's average price of ${position.averagePrice}",
            )
        }
        validateSlice(ticker, creation.strategyId, creation.sellQuantity)

        harvestPairs.save(current.month.id, creation.copy(ticker = ticker), loss)
        return fetch()
    }

    // Null when no month is open. The write returns the recomputed report, so the toggle's effect
    // (a proposal moving to the ignored list) is visible without a second request.
    suspend fun setOptOut(ticker: String, optedOut: Boolean): HarvestReport? {
        months.fetchCurrent() ?: return null
        optOuts.set(ticker.normalizedTicker(), optedOut)
        return fetch()
    }

    // The plan's still-unexecuted stock sells are what the harvest runs after: their proceeds draw
    // down the headroom and their quantities come out of the custody the pass may sell. An executed
    // sell is already in the meter and the ledger, so only a SUGGESTED order's remaining quantity
    // counts.
    private suspend fun afterRebalancing(
        monthId: Int,
        positions: List<TickerAveragePrice>,
        assetKinds: Map<String, AssetKind>,
        grossSales: BigDecimal,
    ): PostRebalance {
        val planned = suggestedOrders.fetchBy(monthId)
            .filter {
                it.status == SuggestedOrderStatus.SUGGESTED &&
                    it.side.tradeSide == TradeSide.SELL &&
                    assetKinds[it.ticker] == AssetKind.STOCK
            }
            .map {
                PlannedStockSell(
                    ticker = it.ticker,
                    quantity = it.quantity.subtract(it.executedQuantity),
                    price = it.suggestedPrice,
                )
            }
            .filter { it.quantity.signum() > 0 }
        val plannedByTicker = planned.groupBy { it.ticker }
            .mapValues { (_, sales) -> sales.sumOf { it.quantity } }
        val plannedProceeds = planned.sumOf { sell ->
            sell.price?.multiply(sell.quantity) ?: BigDecimal.ZERO
        }
        val remainingPositions = positions.map { position ->
            val remaining = position.quantity.subtract(plannedByTicker[position.ticker] ?: BigDecimal.ZERO)
            position.copy(quantity = remaining.max(BigDecimal.ZERO))
        }
        return PostRebalance(
            positions = remainingPositions,
            headroom = StockTaxCalculator.EXEMPTION_LIMIT
                .subtract(grossSales)
                .subtract(plannedProceeds)
                .max(BigDecimal.ZERO),
        )
    }

    // A strategy id is the Attribution slice the rebuy returns to, so it must be a slice of this
    // ticker and must hold the sold units. When the ticker has slices, the pair must name one; a
    // fully unattributed ticker has nothing to preserve.
    private suspend fun validateSlice(ticker: String, strategyId: Int?, quantity: BigDecimal) {
        val slices = attributions.fetchSlicesByTicker()[ticker].orEmpty()
        if (slices.isEmpty()) {
            if (strategyId != null) {
                throw InvalidHarvestPairException("$ticker has no Attribution slice for strategy $strategyId")
            }
            return
        }
        if (strategyId == null) {
            throw InvalidHarvestPairException("$ticker is attributed, so the pair needs a strategy slice")
        }

        val slice = slices.firstOrNull { it.strategyId == strategyId }
            ?: throw InvalidHarvestPairException("$ticker has no Attribution slice for strategy $strategyId")
        if (quantity > slice.quantity) {
            throw InvalidHarvestPairException(
                "strategy $strategyId holds ${slice.quantity} of $ticker, but the sell leg trades $quantity",
            )
        }
    }

    private data class PlannedStockSell(
        val ticker: String,
        val quantity: BigDecimal,
        val price: BigDecimal?,
    )

    private data class PostRebalance(
        val positions: List<TickerAveragePrice>,
        val headroom: BigDecimal,
    )
}
