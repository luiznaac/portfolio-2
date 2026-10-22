package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.listedasset.AttributionService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.order.model.tickerSlices
import dev.agner.portfolio.usecase.portfolio.AllocationService
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.StrategyService
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service
import java.math.BigDecimal

// The order plan's read side: gathers the open month's Capital, Floor and Targets, the projected
// positions and the latest close for every ticker the chain can leaf on, plus the NEUTRO and
// Attribution inputs the leaf carries, then hands them to the pure calculator. At the funding phase
// it adds the funding pass, whose Uninvested Balance is the one input that needs the whole book's
// value — a shallower read never touches that path. computePlan never writes — the persisted order
// lifecycle is a separate slice.
@Service
class OrderPlanService(
    private val months: MonthService,
    private val positions: PositionService,
    private val quotes: QuoteService,
    private val allocations: AllocationService,
    private val strategies: StrategyService,
    private val attributions: AttributionService,
) {

    // Null when no month is open — the edge maps that to the 404 the current-month reads promise.
    suspend fun computePlan(phase: OrderPlanPhase): OrderPlan? {
        val current = months.fetchCurrent() ?: return null
        val targets = current.targets.normalized()
        val positionByTicker = positions.fetchAll().associate { it.ticker to it.quantity }

        // A targeted ticker with no position still needs a price: it is exactly where a NEW_ENTRY
        // candidate comes from. A ticker with neither a target nor a position never reaches the plan.
        val tickers = positionByTicker.keys + targets.tickers.map { it.ticker }
        val prices = tickers
            .mapAsync { ticker -> ticker to quotes.latestClose(ticker) }
            .awaitAll()
            .mapNotNull { (ticker, price) -> if (price == null) null else ticker to price }
            .toMap()

        val plan = OrderPlanCalculator.compute(
            OrderPlanContext(
                period = current.month.period,
                phase = phase,
                capital = current.month.capital,
                floor = current.month.floor,
                targets = targets,
                positions = positionByTicker,
                prices = prices,
                neutroSlices = strategies.fetchNeutroSlices(),
                attributionBySlice = attributions.fetchLedger(),
            ),
        )
        if (!phase.includesFunding) return plan

        return plan.withFunding(
            holdingsValue = allocations.fetchCurrent()?.actual ?: return null,
            positions = positionByTicker,
            prices = prices,
        )
    }

    // The whole book's Actual is exactly the holdings the Capital is not sitting in cash; the
    // Uninvested Balance is the rest. Appends the funding SELLs to the candidates and the summary to
    // the plan — the phase's cumulative depth, composed here rather than in the pure calculator.
    private fun OrderPlan.withFunding(
        holdingsValue: BigDecimal,
        positions: TickerPositions,
        prices: TickerPrices,
    ): OrderPlan {
        val funding = FundingCalculator.compute(
            FundingContext(
                candidates = candidates,
                slices = tickerSlices,
                positions = positions,
                prices = prices,
                floor = floor,
                uninvestedBalance = capital.subtract(holdingsValue),
            ),
        )
        return copy(candidates = candidates + funding.sells, funding = funding.summary)
    }
}
