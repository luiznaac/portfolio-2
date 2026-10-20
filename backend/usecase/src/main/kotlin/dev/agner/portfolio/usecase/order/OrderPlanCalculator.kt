package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.deficitAgainst
import dev.agner.portfolio.usecase.order.model.ClassOrderPlan
import dev.agner.portfolio.usecase.order.model.EquitiesOrderPlan
import dev.agner.portfolio.usecase.order.model.FixedIncomeOrderPlan
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.StrategyOrderPlan
import dev.agner.portfolio.usecase.order.model.SubClassOrderPlan
import dev.agner.portfolio.usecase.order.model.TickerOrderPlan
import dev.agner.portfolio.usecase.portfolio.TargetChainCalculator
import dev.agner.portfolio.usecase.portfolio.model.ClassChain
import dev.agner.portfolio.usecase.portfolio.model.EquitiesChain
import dev.agner.portfolio.usecase.portfolio.model.FixedIncomeChain
import java.math.BigDecimal
import java.math.RoundingMode

// The rebalancing engine's first layers, pure and repository-free so it is unit-tested on the worked
// acceptance numbers (backend/AGENTS.md: a calculation-heavy feature gets its own class).
//
// The ideal chain is scaled against the Investable Capital (Capital − the cash Floor) by the shared
// TargetChainCalculator. The IDEALS phase stops there; CANDIDATES adds the orders the chain implies,
// emitted at the leaves only: one candidate per listed ticker, with its target quantity summed over
// the strategies that hold it. Whole units floor both ways; the remainder stays in the Uninvested
// Balance. A targeted surplus is held — SELLs arrive with funding.
object OrderPlanCalculator {

    fun compute(context: OrderPlanContext): OrderPlan {
        val investableCapital = (context.capital - context.floor).defaultScale()
        val chain = TargetChainCalculator.compute(investableCapital, context.targets)
        val classes = chain.classes.map { toPlan(chain = it, prices = context.prices) }

        return OrderPlan(
            period = context.period,
            phase = context.phase,
            capital = context.capital.defaultScale(),
            floor = context.floor.defaultScale(),
            investableCapital = investableCapital,
            classes = classes,
            candidates = if (context.phase.includesCandidates) {
                computeCandidates(
                    classes = classes,
                    positions = context.positions,
                    prices = context.prices,
                )
            } else {
                emptyList()
            },
        )
    }

    // The chain's rungs map one-to-one; the order plan adds the quote-derived target quantity at each
    // ticker leaf. A ticker without a quote still shows its Ideal but has no target units.
    private fun toPlan(chain: ClassChain, prices: TickerPrices): ClassOrderPlan =
        when (chain) {
            is EquitiesChain -> EquitiesOrderPlan(
                assetClass = chain.assetClass,
                target = chain.target,
                ideal = chain.ideal,
                strategies = chain.strategies.map { strategy ->
                    StrategyOrderPlan(
                        strategy = strategy.strategy,
                        target = strategy.target,
                        ideal = strategy.ideal,
                        tickers = strategy.tickers.map { ticker ->
                            val price = prices[ticker.ticker]?.takeIf { it.signum() > 0 }
                            TickerOrderPlan(
                                ticker = ticker.ticker,
                                target = ticker.target,
                                ideal = ticker.ideal,
                                price = price,
                                targetQuantity = price?.let { unitsFor(amount = ticker.ideal, price = it) },
                            )
                        },
                    )
                },
            )

            is FixedIncomeChain -> FixedIncomeOrderPlan(
                target = chain.target,
                ideal = chain.ideal,
                subClasses = chain.subClasses.map { subClass ->
                    SubClassOrderPlan(
                        subClass = subClass.subClass,
                        target = subClass.target,
                        ideal = subClass.ideal,
                    )
                },
            )
        }

    // One candidate per listed ticker, at the ticker leaf. `hasWeight` distinguishes a deliberately
    // dropped ticker (weight 0 everywhere) from a targeted one whose Ideal is simply small.
    private fun computeCandidates(
        classes: List<ClassOrderPlan>,
        positions: TickerPositions,
        prices: TickerPrices,
    ): List<OrderCandidate> {
        val slices = classes
            .filterIsInstance<EquitiesOrderPlan>()
            .flatMap { it.strategies }
            .flatMap { it.tickers }
            .groupBy { it.ticker }
        val tickers = (slices.keys + positions.keys)
            .sorted()
            .associateWith { slices[it].orEmpty() }

        return tickers.mapNotNull { (ticker, tickerSlices) ->
            val position = positions[ticker] ?: BigDecimal.ZERO
            val price = prices[ticker]?.takeIf { it.signum() > 0 }
            val hasWeight = tickerSlices.any { it.target.signum() > 0 }

            when {
                hasWeight && price != null ->
                    buyOrNewEntry(ticker = ticker, slices = tickerSlices, position = position, price = price)
                !hasWeight && position.signum() > 0 -> exit(ticker = ticker, position = position, price = price)
                else -> null
            }
        }
    }

    private fun buyOrNewEntry(
        ticker: String,
        slices: List<TickerOrderPlan>,
        position: BigDecimal,
        price: BigDecimal,
    ): OrderCandidate? {
        val targetQuantity = slices.mapNotNull { it.targetQuantity }.sumOf { it }
        val isNewEntry = position.signum() == 0 && targetQuantity.signum() > 0
        // Whole units floor the net, so a shortfall under one unit is a fractional leftover with no
        // order — it stays idle rather than becoming a zero-quantity BUY.
        val quantity = if (isNewEntry) targetQuantity else wholeUnits(targetQuantity - position)
        if (quantity.signum() <= 0) return null

        return candidate(
            ticker = ticker,
            side = if (isNewEntry) OrderSide.NEW_ENTRY else OrderSide.BUY,
            quantity = quantity,
            ideal = slices.sumOf { it.ideal },
            targetQuantity = targetQuantity,
            position = position,
            price = price,
        )
    }

    // A ticker outside every strategy (or at weight 0 everywhere) is held, and the manual EXIT it
    // defaults to takes the full position — whole units, so a fractional remainder stays put. This
    // is not a SELL of a targeted surplus: a covered surplus still emits no candidate.
    private fun exit(ticker: String, position: BigDecimal, price: BigDecimal?): OrderCandidate? {
        val quantity = wholeUnits(position)
        if (quantity.signum() <= 0) return null

        return candidate(
            ticker = ticker,
            side = OrderSide.EXIT,
            quantity = quantity,
            ideal = BigDecimal.ZERO,
            targetQuantity = BigDecimal.ZERO,
            position = position,
            price = price,
        )
    }

    private fun candidate(
        ticker: String,
        side: OrderSide,
        quantity: BigDecimal,
        ideal: BigDecimal,
        targetQuantity: BigDecimal,
        position: BigDecimal,
        price: BigDecimal?,
    ) = OrderCandidate(
        ticker = ticker,
        side = side,
        quantity = quantity,
        price = price,
        amount = amount(quantity = quantity, price = price),
        deficit = price?.let { ideal.deficitAgainst(position.multiply(it)) },
        ideal = ideal.defaultScale(),
        targetQuantity = targetQuantity,
        positionQuantity = position,
    )

    private fun amount(quantity: BigDecimal, price: BigDecimal?): BigDecimal? =
        price?.let { quantity.multiply(it).defaultScale() }

    // Whole units floor down, so a sub-unit remainder is never bought or sold.
    private fun unitsFor(amount: BigDecimal, price: BigDecimal): BigDecimal =
        amount.divide(price, 0, RoundingMode.FLOOR)

    private fun wholeUnits(quantity: BigDecimal): BigDecimal = quantity.setScale(0, RoundingMode.FLOOR)
}
