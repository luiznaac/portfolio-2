package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.nextWeekday
import dev.agner.portfolio.usecase.commons.percentOf
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import dev.agner.portfolio.usecase.tax.model.FiiHarvestReport
import dev.agner.portfolio.usecase.tax.model.HarvestComputation
import dev.agner.portfolio.usecase.tax.model.HarvestContext
import dev.agner.portfolio.usecase.tax.model.HarvestIgnored
import dev.agner.portfolio.usecase.tax.model.HarvestLeg
import dev.agner.portfolio.usecase.tax.model.HarvestProposal
import dev.agner.portfolio.usecase.tax.model.TickerAveragePrice
import java.math.BigDecimal
import java.math.RoundingMode

// The tax-loss harvest pass: rank the held STOCK positions with an unrealized loss, largest loss
// first, and pair a SELL with a REBUY on the next business day — never the same day — sized to the
// month's remaining R$20k headroom. A sell at a loss realizes a loss for the carry bucket instead of
// a taxable gain, and staying inside the headroom keeps the month's other gains exempt.
//
// FIIs have no exemption to fill and their losses are species-bound, so they are reported at 20%
// (gain) / zero (loss) and never proposed. A candidate whose sized loss misses the minimum gate, or
// whose slice cannot supply at least one unit, is dropped. Pure and side-effect free.
object HarvestCalculator {

    // Lei 8.668/1993 art. 16-A, as amended: an FII gain is taxed at 20% and has no exemption, so the
    // report's liability estimate is the gain times this rate.
    private val FII_GAIN_RATE: BigDecimal = BigDecimal("20.00")
    fun compute(context: HarvestContext): HarvestComputation {
        val (ignored, active) = rankedCandidates(context).partition { it.ticker in context.optOuts }
        val allocation = active.fold(
            Allocation(context.headroom.max(BigDecimal.ZERO), emptyList()),
        ) { current, candidate ->
            current.consume(sized(context, candidate, current.headroom))
        }

        return HarvestComputation(
            suggestions = allocation.proposals,
            ignored = ignored.map { it.toIgnored(context) },
            fiiReport = fiiReport(context),
        )
    }

    // Loss descending by the position's whole unrealized loss — the largest loss gets the headroom
    // first, even when an Attribution slice limits how much of it can be harvested. The ticker breaks
    // ties so two equal losses never swap order between reads.
    private fun rankedCandidates(context: HarvestContext): List<Candidate> =
        context.positions
            .filter {
                it.quantity.signum() > 0 &&
                    it.ticker !in context.acceptedTickers &&
                    context.assetKinds[it.ticker] == AssetKind.STOCK
            }
            .mapNotNull { position -> candidateOf(context, position) }
            .sortedWith(compareByDescending<Candidate> { it.positionLoss }.thenBy { it.ticker })

    private fun candidateOf(context: HarvestContext, position: TickerAveragePrice): Candidate? {
        val price = context.prices[position.ticker] ?: return null
        if (price.signum() <= 0 || position.averagePrice <= price) return null

        // The sell must come from one Attribution slice so the rebuy can return to it; the largest
        // slice is the source. An unattributed position has nothing to preserve and sells freely.
        val slice = context.slicesByTicker[position.ticker]?.maxByOrNull { it.quantity }
        val available = slice?.quantity?.let { minOf(position.quantity, it) } ?: position.quantity
        if (available < BigDecimal.ONE) return null

        val unitLoss = position.averagePrice.subtract(price)
        return Candidate(
            position = position,
            price = price,
            slice = slice,
            availableQuantity = available,
            unitLoss = unitLoss,
            positionLoss = unitLoss.multiply(position.quantity),
            availableLoss = unitLoss.multiply(available),
        )
    }

    // Whole units only (the order engine's convention) and never more than the headroom allows. The
    // gate is checked after sizing, because partial sizing is exactly what can push a candidate
    // below the R$500 minimum.
    private fun sized(context: HarvestContext, candidate: Candidate, headroom: BigDecimal): HarvestProposal? {
        val byHeadroom = headroom.divide(candidate.price, 0, RoundingMode.DOWN)
        val quantity = minOf(candidate.availableQuantity, byHeadroom)
        val loss = candidate.unitLoss.multiply(quantity)
        if (quantity < BigDecimal.ONE || loss < context.minimumLoss) return null

        val amount = quantity.multiply(candidate.price).defaultScale()
        return HarvestProposal(
            ticker = candidate.ticker,
            strategyId = candidate.slice?.strategyId,
            strategy = candidate.slice?.strategyName,
            quantity = quantity,
            averagePrice = candidate.position.averagePrice,
            price = candidate.price,
            loss = loss.defaultScale(),
            neutro = candidate.neutro(context),
            sell = HarvestLeg(
                date = context.sellDate,
                quantity = quantity,
                price = candidate.price,
                amount = amount,
            ),
            rebuy = HarvestLeg(
                date = context.sellDate.nextWeekday(),
                quantity = quantity,
                price = candidate.price,
                amount = amount,
            ),
        )
    }

    private fun fiiReport(context: HarvestContext): List<FiiHarvestReport> =
        context.positions
            .filter { it.quantity.signum() > 0 && context.assetKinds[it.ticker] == AssetKind.FII }
            .mapNotNull { position ->
                val price = context.prices[position.ticker] ?: return@mapNotNull null
                val result = price.subtract(position.averagePrice).multiply(position.quantity)
                val tax = result.max(BigDecimal.ZERO).percentOf(FII_GAIN_RATE)
                FiiHarvestReport(
                    ticker = position.ticker,
                    quantity = position.quantity,
                    price = price,
                    averagePrice = position.averagePrice,
                    unrealizedResult = result.defaultScale(),
                    estimatedTax = tax,
                )
            }
            .sortedBy { it.ticker }

    private data class Candidate(
        val position: TickerAveragePrice,
        val price: BigDecimal,
        val slice: AttributionSlice?,
        val availableQuantity: BigDecimal,
        val unitLoss: BigDecimal,
        val positionLoss: BigDecimal,
        val availableLoss: BigDecimal,
    ) {
        val ticker: String get() = position.ticker

        fun neutro(context: HarvestContext): Boolean =
            slice?.let { StrategyTicker(it.strategyName, ticker) in context.neutroSlices } ?: false

        fun toIgnored(context: HarvestContext) = HarvestIgnored(
            ticker = ticker,
            strategyId = slice?.strategyId,
            strategy = slice?.strategyName,
            quantity = availableQuantity.defaultScale(),
            loss = availableLoss.defaultScale(),
            neutro = neutro(context),
        )
    }

    // The running headroom and the pairs it has paid for, folded across the ranked candidates. A
    // candidate whose sized pair misses the gate consumes nothing.
    private data class Allocation(
        val headroom: BigDecimal,
        val proposals: List<HarvestProposal>,
    ) {
        fun consume(proposal: HarvestProposal?) = if (proposal == null) {
            this
        } else {
            Allocation(headroom.subtract(proposal.sell.amount), proposals + proposal)
        }
    }
}
