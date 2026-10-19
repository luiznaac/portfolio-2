package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.quotes.QuoteService
import kotlinx.coroutines.awaitAll
import org.springframework.stereotype.Service

// The order plan's read side: gathers the open month's Capital, Floor and Targets, the projected
// positions and the latest close for every ticker the chain can leaf on, then hands them to the pure
// calculator. computePlan never writes — the persisted order lifecycle is a separate slice.
@Service
class OrderPlanService(
    private val months: MonthService,
    private val positions: PositionService,
    private val quotes: QuoteService,
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

        return OrderPlanCalculator.compute(
            OrderPlanContext(
                period = current.month.period,
                phase = phase,
                capital = current.month.capital,
                floor = current.month.floor,
                targets = targets,
                positions = positionByTicker,
                prices = prices,
            ),
        )
    }
}
