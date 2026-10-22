package dev.agner.portfolio.usecase.order

import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.deficitAgainst
import dev.agner.portfolio.usecase.order.model.ClassOrderPlan
import dev.agner.portfolio.usecase.order.model.EquitiesOrderPlan
import dev.agner.portfolio.usecase.order.model.FixedIncomeOrder
import dev.agner.portfolio.usecase.order.model.FixedIncomeOrderPlan
import dev.agner.portfolio.usecase.order.model.FixedIncomeOrderSide
import dev.agner.portfolio.usecase.order.model.OrderCandidate
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.OutsideStrategyHolding
import dev.agner.portfolio.usecase.order.model.StrategyOrderPlan
import dev.agner.portfolio.usecase.order.model.SubClassOrderPlan
import dev.agner.portfolio.usecase.order.model.TickerOrderPlan
import dev.agner.portfolio.usecase.order.model.targetUnits
import dev.agner.portfolio.usecase.portfolio.TargetChainCalculator
import dev.agner.portfolio.usecase.portfolio.model.ClassChain
import dev.agner.portfolio.usecase.portfolio.model.EquitiesChain
import dev.agner.portfolio.usecase.portfolio.model.FixedIncomeChain
import dev.agner.portfolio.usecase.portfolio.model.SubClassChain
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import java.math.BigDecimal
import java.math.RoundingMode

// The rebalancing engine's first layers, pure and repository-free so it is unit-tested on the worked
// acceptance numbers (backend/AGENTS.md: a calculation-heavy feature gets its own class).
//
// The ideal chain is scaled against the Investable Capital (Capital − the cash Floor) by the shared
// TargetChainCalculator. The IDEALS phase stops there; CANDIDATES adds the orders the chain implies,
// emitted at the leaves only: one candidate per listed ticker, with its target quantity summed over
// the strategies that hold it. A NEUTRO slice contributes no buy target, so its deficit stays
// visible on the leaf and unfunded. Whole units floor both ways; the remainder stays in the
// Uninvested Balance. A targeted surplus is held — SELLs arrive with funding, which the service
// composes from FundingCalculator at the funding phase.
//
// A held ticker no Target names (a dropped ticker) is reported as an OutsideStrategyHolding with a
// full-position manual EXIT default; it is never auto-exited. RF orders are sized per sub-class at
// the full phase.
object OrderPlanCalculator {

    fun compute(context: OrderPlanContext): OrderPlan {
        val investableCapital = (context.capital - context.floor).defaultScale()
        val chain = TargetChainCalculator.compute(investableCapital, context.targets)
        val classes = chain.classes.map { toPlan(chain = it, context = context) }
        val candidates = if (context.phase.includesCandidates) {
            computeCandidates(
                classes = classes,
                positions = context.positions,
                prices = context.prices,
            )
        } else {
            emptyList()
        }
        val outsideStrategy = if (context.phase.includesCandidates) {
            computeOutsideStrategy(classes = classes, positions = context.positions, prices = context.prices)
        } else {
            emptyList()
        }

        return OrderPlan(
            period = context.period,
            phase = context.phase,
            capital = context.capital.defaultScale(),
            floor = context.floor.defaultScale(),
            investableCapital = investableCapital,
            classes = classes,
            candidates = candidates,
            outsideStrategy = outsideStrategy,
        )
    }

    // The chain's rungs map one-to-one; the order plan adds the quote-derived target quantity at each
    // ticker leaf. A ticker without a quote still shows its Ideal but has no target units. The leaf
    // also carries the NEUTRO flag and the stored attribution quantity its funding math needs.
    private fun toPlan(chain: ClassChain, context: OrderPlanContext): ClassOrderPlan =
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
                            val price = context.prices[ticker.ticker]?.takeIf { it.signum() > 0 }
                            val slice = StrategyTicker(strategy.strategy, ticker.ticker)
                            val attributed = context.attributionBySlice[slice] ?: BigDecimal.ZERO
                            val custody = attributed.coerceAtMost(context.positions[ticker.ticker] ?: BigDecimal.ZERO)
                            TickerOrderPlan(
                                ticker = ticker.ticker,
                                strategy = strategy.strategy,
                                target = ticker.target,
                                ideal = ticker.ideal,
                                price = price,
                                targetQuantity = price?.let { unitsFor(amount = ticker.ideal, price = it) },
                                neutro = slice in context.neutroSlices,
                                attributedQuantity = attributed,
                                deficit = price?.let { ticker.ideal.deficitAgainst(custody.multiply(it)) },
                            )
                        },
                    )
                },
            )

            is FixedIncomeChain -> FixedIncomeOrderPlan(
                target = chain.target,
                ideal = chain.ideal,
                subClasses = chain.subClasses.map { subClass ->
                    val actual = if (context.phase.isFull) {
                        context.fixedIncomeActualBySubClass[subClass.subClass] ?: BigDecimal.ZERO
                    } else {
                        null
                    }
                    val deficit = actual?.let { subClass.ideal.deficitAgainst(it) }
                    SubClassOrderPlan(
                        subClass = subClass.subClass,
                        target = subClass.target,
                        ideal = subClass.ideal,
                        actual = actual,
                        deficit = deficit,
                        orders = if (context.phase.isFull) {
                            fixedIncomeOrders(subClass = subClass, deficit = deficit)
                        } else {
                            emptyList()
                        },
                    )
                },
            )
        }

    // One candidate per listed ticker, at the ticker leaf. `hasWeight` distinguishes a deliberately
    // dropped ticker (weight 0 everywhere) from a targeted one whose Ideal is simply small. A NEUTRO
    // slice keeps its weight but contributes no buy target; the units attributed to it stay out of
    // the custody an active slice may net against, so a NEUTRO holding can never mask a sibling
    // slice's deficit. A ticker held entirely under NEUTRO still counts as targeted, so it is not
    // mistaken for a dropped ticker and never becomes an outside-strategy holding.
    private fun computeCandidates(
        classes: List<ClassOrderPlan>,
        positions: TickerPositions,
        prices: TickerPrices,
    ): List<OrderCandidate> {
        val slices = tickerSlices(classes).groupBy { it.ticker }
        val targeted = targetedTickers(classes)
        val tickers = (slices.keys + positions.keys)
            .sorted()
            .associateWith { slices[it].orEmpty() }

        return tickers.mapNotNull { (ticker, tickerSlices) ->
            val position = positions[ticker] ?: BigDecimal.ZERO
            val price = prices[ticker]?.takeIf { it.signum() > 0 }
            val hasWeight = ticker in targeted

            if (hasWeight && price != null) {
                buyOrNewEntry(
                    ticker = ticker,
                    slices = tickerSlices.filterNot { it.neutro },
                    position = position,
                    neutroHeld = tickerSlices.filter { it.neutro }.sumOf { it.attributedQuantity },
                    price = price,
                )
            } else {
                null
            }
        }
    }

    // Every held ticker the chain does not name: a dropped ticker (weight 0 everywhere) or a position
    // from before the current Edition. Held for attention, with a manual EXIT defaulting to the full
    // whole-unit position — the engine emits no order here.
    private fun computeOutsideStrategy(
        classes: List<ClassOrderPlan>,
        positions: TickerPositions,
        prices: TickerPrices,
    ): List<OutsideStrategyHolding> {
        val targeted = targetedTickers(classes)

        return positions.entries
            .filter { (ticker, position) -> position.signum() > 0 && ticker !in targeted }
            .map { (ticker, position) ->
                val price = prices[ticker]?.takeIf { it.signum() > 0 }
                OutsideStrategyHolding(
                    ticker = ticker,
                    quantity = position,
                    price = price,
                    amount = price?.let { position.multiply(it).defaultScale() },
                    suggestedExitQuantity = wholeUnits(position),
                )
            }
            .sortedBy { it.ticker }
    }

    private fun buyOrNewEntry(
        ticker: String,
        slices: List<TickerOrderPlan>,
        position: BigDecimal,
        neutroHeld: BigDecimal,
        price: BigDecimal,
    ): OrderCandidate? {
        val targetQuantity = slices.targetUnits
        val isNewEntry = position.signum() == 0 && targetQuantity.signum() > 0
        // Units attributed to a NEUTRO slice are not available to an active slice: they are either
        // held (the NEUTRO rule) or sold through funding, never counted as meeting an active Ideal.
        // Without an attribution ledger `neutroHeld` is zero and the whole position nets as before.
        val availableCustody = (position - neutroHeld.coerceAtMost(position)).coerceAtLeast(BigDecimal.ZERO)
        // Whole units floor the net, so a shortfall under one unit is a fractional leftover with no
        // order — it stays idle rather than becoming a zero-quantity BUY.
        val quantity = if (isNewEntry) targetQuantity else wholeUnits(targetQuantity - availableCustody)
        if (quantity.signum() <= 0) return null

        return OrderCandidate.traded(
            side = if (isNewEntry) OrderSide.NEW_ENTRY else OrderSide.BUY,
            ticker = ticker,
            quantity = quantity,
            price = price,
            ideal = slices.sumOf { it.ideal },
            targetQuantity = targetQuantity,
            position = position,
            custody = availableCustody,
        )
    }

    // Whole units floor down, so a sub-unit remainder is never bought or sold.
    private fun unitsFor(amount: BigDecimal, price: BigDecimal): BigDecimal =
        amount.divide(price, 0, RoundingMode.FLOOR)

    private fun wholeUnits(quantity: BigDecimal): BigDecimal = quantity.setScale(0, RoundingMode.FLOOR)

    // The RF leaf orders: a targeted sub-class contributes its deficit or redeems its surplus, in
    // whole reais. A sub-class the chain does not target has no order — an untargeted surplus is held.
    private fun fixedIncomeOrders(subClass: SubClassChain, deficit: BigDecimal?): List<FixedIncomeOrder> {
        if (subClass.target == null || deficit == null || deficit.signum() == 0) return emptyList()
        return listOf(
            FixedIncomeOrder(
                subClass = subClass.subClass,
                side = if (deficit.signum() > 0) FixedIncomeOrderSide.CONTRIBUTE else FixedIncomeOrderSide.REDEEM,
                amount = deficit.abs().defaultScale(),
            ),
        )
    }

    // The listed ticker leaves, in chain order.
    private fun tickerSlices(classes: List<ClassOrderPlan>): List<TickerOrderPlan> =
        classes.filterIsInstance<EquitiesOrderPlan>().flatMap { it.strategies }.flatMap { it.tickers }

    // The tickers the chain still targets (weight > 0): everything else held is outside the strategy.
    private fun targetedTickers(classes: List<ClassOrderPlan>): Set<String> =
        tickerSlices(classes).filter { it.target.signum() > 0 }.map { it.ticker }.toSet()
}
