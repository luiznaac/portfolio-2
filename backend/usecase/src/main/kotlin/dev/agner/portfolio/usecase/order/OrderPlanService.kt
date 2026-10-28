package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.listedasset.AttributionService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.order.model.tickerSlices
import dev.agner.portfolio.usecase.portfolio.AllocationService
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.TargetChainCalculator
import dev.agner.portfolio.usecase.portfolio.model.Allocation
import dev.agner.portfolio.usecase.portfolio.targetedTickers
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service
import java.math.BigDecimal

// The order plan's read side: gathers the open month's Capital, Floor and Targets, the projected
// positions and the latest close for every ticker the chain can leaf on, plus the NEUTRO and
// Attribution inputs the leaf carries, then hands them to the pure calculator. At the funding phase
// it adds the funding pass, whose Uninvested Balance is the one input that needs the whole book's
// value — a shallower read never touches that path. The full phase then applies the pro-rata scaling,
// the attribution bias and transfers, and the RF sub-class orders. computePlan never writes — the
// persisted order lifecycle is a separate slice.
@Service
class OrderPlanService(
    private val months: MonthService,
    private val positions: PositionService,
    private val quotes: QuoteService,
    private val allocations: AllocationService,
    private val strategies: StrategyService,
    private val attributions: AttributionService,
    private val catalog: CatalogService,
) {

    // Null when no month is open — the edge maps that to the 404 the current-month reads promise.
    suspend fun computePlan(phase: OrderPlanPhase): OrderPlan? {
        val current = months.fetchCurrent() ?: return null
        val targets = current.targets.normalized()
        val positionByTicker = positions.fetchAll().associate { it.ticker to it.quantity }
        val ledger = attributions.fetchLedger()
        val manualNames = strategies.fetchManualNames()
        // The report-membership half comes from the chain leaves, exactly as the order calculator
        // reads it, so a Target the chain does not carry cannot make a ticker inside.
        val investableCapital = (current.month.capital - current.month.floor).defaultScale()
        val placement = PlacementCalculator.compute(
            positions = positionByTicker,
            targetedTickers = TargetChainCalculator.compute(investableCapital, targets).targetedTickers(),
            ledger = ledger,
            manualStrategies = manualNames,
            unclassifiedTickers = catalog.fetchUnclassified(positionByTicker.keys),
        )

        // A targeted ticker with no position still needs a price: it is exactly where a NEW_ENTRY
        // candidate comes from. A ticker with neither a target nor a position never reaches the plan.
        val tickers = positionByTicker.keys + targets.tickers.map { it.ticker }
        val prices = tickers
            .mapAsync { ticker -> ticker to quotes.latestClose(ticker) }
            .awaitAll()
            .mapNotNull { (ticker, price) -> if (price == null) null else ticker to price }
            .toMap()

        // The funding pass and the full phase need the whole book's Actual: its listed part prices the
        // Uninvested Balance and its RF part sizes the sub-class orders.
        val allocation = if (phase.includesFunding) allocations.fetchCurrent() else null

        val context = OrderPlanContext(
            period = current.month.period,
            phase = phase,
            capital = current.month.capital,
            floor = current.month.floor,
            targets = targets,
            positions = positionByTicker,
            prices = prices,
            placement = placement,
            neutroSlices = strategies.fetchNeutroSlices(),
            attributionBySlice = ledger,
            manualSlices = ledger.filterKeys { it.strategy in manualNames },
            fixedIncomeActualBySubClass = allocation?.fixedIncomeActual.orEmpty(),
        )
        val plan = OrderPlanCalculator.compute(context)
        if (allocation == null) return plan

        val funded = plan.withFunding(
            holdingsValue = allocation.actual,
            positions = positionByTicker,
            prices = prices,
            placement = placement,
        )
        return if (phase.isFull) {
            funded.withFullPlan(manualSlices = context.manualSlices)
        } else {
            funded
        }
    }

    // The whole book's Actual is exactly the holdings the Capital is not sitting in cash; the
    // Uninvested Balance is the rest. Appends the funding SELLs to the candidates and the summary to
    // the plan — the phase's cumulative depth, composed here rather than in the pure calculator.
    private fun OrderPlan.withFunding(
        holdingsValue: BigDecimal,
        positions: TickerPositions,
        prices: TickerPrices,
        placement: TickerPlacements,
    ): OrderPlan {
        val funding = FundingCalculator.compute(
            FundingContext(
                candidates = candidates,
                slices = tickerSlices,
                positions = positions,
                prices = prices,
                placement = placement,
                floor = floor,
                uninvestedBalance = capital.subtract(holdingsValue),
            ),
        )
        return copy(candidates = candidates + funding.sells, funding = funding.summary)
    }

    // The full phase's last two layers: scale the buys to what the funding pass could deploy, then
    // aim the attribution at the scaled orders and finish with the cross-slice transfers. The funding
    // summary is restated in scaled terms so the plan never reports buying more than it can fund.
    // `manualSlices` is the bucket half of the ledger, so the transfer pass can suggest moving its
    // units into a short report slice.
    private fun OrderPlan.withFullPlan(manualSlices: Map<StrategyTicker, BigDecimal>): OrderPlan {
        val funding = funding ?: return this
        val proRata = ProRataCalculator.compute(candidates = candidates, funding = funding)
        val attribution = AttributionPlanner.compute(
            slices = tickerSlices,
            candidates = proRata.candidates,
            manualSlices = manualSlices,
        )
        return copy(
            candidates = proRata.candidates,
            funding = funding.copy(
                buyAmount = proRata.summary.buyAmount,
                unfundedAmount = proRata.summary.unfundedAmount,
            ),
            proRata = proRata.summary,
            attribution = attribution.moves,
            transfers = attribution.transfers,
        )
    }
}

// The RF sub-class Actuals the full phase's orders need, keyed the way the chain addresses them.
private val Allocation.fixedIncomeActual: SubClassAmounts
    get() = classes
        .firstOrNull { it.assetClass == AssetClass.RENDA_FIXA }
        ?.subClasses
        ?.associate { it.subClass to it.actual }
        .orEmpty()
