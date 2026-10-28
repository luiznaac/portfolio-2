package dev.agner.portfolio.usecase.order.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.commons.defaultScale
import dev.agner.portfolio.usecase.commons.deficitAgainst
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The open month's computed order plan. The ideal chain is computed on read from (Capital, Floor,
// Targets, quotes) and is never stored; only the later lifecycle slice persists the orders it
// suggests. `investableCapital` is Capital minus the cash Floor — the amount every Ideal scales.
// `funding` is the payload of the requested depth: it is absent when a shallower phase was asked
// for, because the phase is a query dimension rather than a variant of the plan's value (the same
// shape `EditionDetail.diffFromPrevious` takes for a first edition).
//
// `outsideStrategy` lists the held tickers no Target names (dropped tickers) and no Manual Strategy
// holds, held for attention rather than auto-exited. `unclassified` is separate: a ticker the
// catalog has not classified is a data-quality state, never an outside holding, and Funding never
// sells it. Both are absent at the ideals phase, which carries the chain alone. `proRata`,
// `attribution` and `transfers` arrive with the full phase.
data class OrderPlan(
    val period: YearMonth,
    val phase: OrderPlanPhase,
    val capital: BigDecimal,
    val floor: BigDecimal,
    val investableCapital: BigDecimal,
    val classes: List<ClassOrderPlan>,
    val candidates: List<OrderCandidate>,
    val funding: FundingSummary? = null,
    val outsideStrategy: List<OutsideStrategyHolding> = emptyList(),
    val unclassified: List<UnclassifiedHolding> = emptyList(),
    val proRata: ProRataSummary? = null,
    val attribution: List<AttributionMove> = emptyList(),
    val transfers: List<AttributionTransfer> = emptyList(),
)

// One class rung of the ideal chain. ACOES/FIIS carry `strategies`; RENDA_FIXA carries
// `subClasses` (RF targets at sub-class level only, so its leaves are the sub-classes). The two
// shapes are variants rather than two lists with one always empty.
sealed interface ClassOrderPlan {
    val assetClass: AssetClass
    val target: BigDecimal?
    val ideal: BigDecimal
}

data class EquitiesOrderPlan(
    override val assetClass: AssetClass,
    override val target: BigDecimal?,
    override val ideal: BigDecimal,
    val strategies: List<StrategyOrderPlan>,
) : ClassOrderPlan

data class FixedIncomeOrderPlan(
    override val target: BigDecimal?,
    override val ideal: BigDecimal,
    val subClasses: List<SubClassOrderPlan>,
) : ClassOrderPlan {
    override val assetClass: AssetClass = AssetClass.RENDA_FIXA
}

// A strategy rung — the second rung of an ACOES/FIIS class.
data class StrategyOrderPlan(
    val strategy: String,
    val target: BigDecimal,
    val ideal: BigDecimal,
    val tickers: List<TickerOrderPlan>,
)

// A ticker leaf under one strategy. The ideal is in R$, the target quantity is the whole-unit floor
// of that slice (null when no quote prices it yet). A ticker under several strategies gets one
// candidate with the summed target quantity — orders are emitted at the ticker leaves only.
//
// `strategy` names the slice's Strategy — the key the Attribution ledger is stored under, so the
// attribution pass can address the slice. `neutro` is the Edition Entry's "não operar" flag for this
// (strategy, ticker): the slice never becomes a buy, and its attributed units are only sold to fund
// other buys. `attributedQuantity` is the stored Attribution slice's quantity, and `deficit` is
// Ideal − attributed × price, capped by custody — the badge the NEUTRO rule leaves visible even
// without a candidate. Both are null-safe for an unpriced ticker, whose deficit cannot be measured
// in R$.
data class TickerOrderPlan(
    val ticker: String,
    val strategy: String,
    val target: BigDecimal,
    val ideal: BigDecimal,
    val price: BigDecimal?,
    val targetQuantity: BigDecimal?,
    val neutro: Boolean = false,
    val attributedQuantity: BigDecimal = BigDecimal.ZERO,
    val deficit: BigDecimal? = null,
)

// The RF sub-class rung — the second and last rung of RENDA_FIXA. All three sub-classes are always
// present, so `target` is null when the chain declares none. `actual`, `deficit` and `orders` arrive
// with the full phase only — a shallower read does not value the RF book. An order is only sized for
// a targeted sub-class: a null target means the chain declares no percentage, and an untargeted
// surplus is held like any other.
data class SubClassOrderPlan(
    val subClass: FixedIncomeSubClass,
    val target: BigDecimal?,
    val ideal: BigDecimal,
    val actual: BigDecimal? = null,
    val deficit: BigDecimal? = null,
    val orders: List<FixedIncomeOrder> = emptyList(),
)

// A leaf order the candidates phase suggests, or the funding pass adds. The side is a sealed
// subtype, so only a funding SELL carries a `fundingSource` and no candidate can hold a field that
// is meaningless for its kind. `quantity` is whole units, floored both ways; the remainder stays
// uninvested. NEW_ENTRY opens a zero position; EXIT closes a dropped ticker's full position. A
// targeted surplus is held, so it never becomes a SELL except through funding.
//
// `deficit` is the leaf's Deficit in R$ (Ideal − custody × price), so its negative side is the
// Surplus — null when no quote values the custody, alongside `amount`. A BUY's custody is the units
// already available to its slices; an EXIT's is the whole position.
sealed class OrderCandidate(
    open val ticker: String,
    open val quantity: BigDecimal,
    open val price: BigDecimal?,
    open val amount: BigDecimal?,
    open val deficit: BigDecimal?,
    open val ideal: BigDecimal,
    open val targetQuantity: BigDecimal,
    open val positionQuantity: BigDecimal,
) {
    abstract val side: OrderSide

    // A pro-rata-scaled candidate keeps its price and deficit; only the traded quantity and its R$
    // amount shrink. Each variant rebuilds itself so no caller has to branch on the side.
    abstract fun withQuantity(quantity: BigDecimal): OrderCandidate

    // The R$ amount a quantity trades at — the one place the scaled variants derive it.
    protected fun amountFor(quantity: BigDecimal): BigDecimal? = price?.multiply(quantity)?.defaultScale()

    data class Buy(
        override val ticker: String,
        override val quantity: BigDecimal,
        override val price: BigDecimal?,
        override val amount: BigDecimal?,
        override val deficit: BigDecimal?,
        override val ideal: BigDecimal,
        override val targetQuantity: BigDecimal,
        override val positionQuantity: BigDecimal,
    ) : OrderCandidate(ticker, quantity, price, amount, deficit, ideal, targetQuantity, positionQuantity) {
        override val side: OrderSide = OrderSide.BUY

        override fun withQuantity(quantity: BigDecimal): OrderCandidate =
            copy(quantity = quantity, amount = amountFor(quantity))
    }

    data class NewEntry(
        override val ticker: String,
        override val quantity: BigDecimal,
        override val price: BigDecimal?,
        override val amount: BigDecimal?,
        override val deficit: BigDecimal?,
        override val ideal: BigDecimal,
        override val targetQuantity: BigDecimal,
        override val positionQuantity: BigDecimal,
    ) : OrderCandidate(ticker, quantity, price, amount, deficit, ideal, targetQuantity, positionQuantity) {
        override val side: OrderSide = OrderSide.NEW_ENTRY

        override fun withQuantity(quantity: BigDecimal): OrderCandidate =
            copy(quantity = quantity, amount = amountFor(quantity))
    }

    data class Exit(
        override val ticker: String,
        override val quantity: BigDecimal,
        override val price: BigDecimal?,
        override val amount: BigDecimal?,
        override val deficit: BigDecimal?,
        override val ideal: BigDecimal,
        override val targetQuantity: BigDecimal,
        override val positionQuantity: BigDecimal,
    ) : OrderCandidate(ticker, quantity, price, amount, deficit, ideal, targetQuantity, positionQuantity) {
        override val side: OrderSide = OrderSide.EXIT

        override fun withQuantity(quantity: BigDecimal): OrderCandidate =
            copy(quantity = quantity, amount = amountFor(quantity))
    }

    data class FundingSell(
        override val ticker: String,
        override val quantity: BigDecimal,
        override val price: BigDecimal?,
        override val amount: BigDecimal?,
        override val deficit: BigDecimal?,
        override val ideal: BigDecimal,
        override val targetQuantity: BigDecimal,
        override val positionQuantity: BigDecimal,
        val fundingSource: FundingSource,
    ) : OrderCandidate(ticker, quantity, price, amount, deficit, ideal, targetQuantity, positionQuantity) {
        override val side: OrderSide = OrderSide.SELL

        override fun withQuantity(quantity: BigDecimal): OrderCandidate =
            copy(quantity = quantity, amount = amountFor(quantity))
    }

    companion object {
        // The trade-reason kinds share their figures, so the amount and deficit are computed once.
        // `custody` is the quantity the deficit is measured against (see the class comment).
        fun traded(
            side: OrderSide,
            ticker: String,
            quantity: BigDecimal,
            price: BigDecimal?,
            ideal: BigDecimal,
            targetQuantity: BigDecimal,
            position: BigDecimal,
            custody: BigDecimal = position,
        ): OrderCandidate {
            val figures = CandidateFigures.of(quantity = quantity, price = price, ideal = ideal, custody = custody)
            return when (side) {
                OrderSide.BUY -> Buy(
                    ticker = ticker,
                    quantity = quantity,
                    price = price,
                    amount = figures.amount,
                    deficit = figures.deficit,
                    ideal = ideal.defaultScale(),
                    targetQuantity = targetQuantity,
                    positionQuantity = position,
                )
                OrderSide.NEW_ENTRY -> NewEntry(
                    ticker = ticker,
                    quantity = quantity,
                    price = price,
                    amount = figures.amount,
                    deficit = figures.deficit,
                    ideal = ideal.defaultScale(),
                    targetQuantity = targetQuantity,
                    positionQuantity = position,
                )
                OrderSide.EXIT -> Exit(
                    ticker = ticker,
                    quantity = quantity,
                    price = price,
                    amount = figures.amount,
                    deficit = figures.deficit,
                    ideal = ideal.defaultScale(),
                    targetQuantity = targetQuantity,
                    positionQuantity = position,
                )
                OrderSide.SELL -> error("a SELL is a funding sell; build it with fundingSell")
            }
        }

        fun fundingSell(
            ticker: String,
            quantity: BigDecimal,
            price: BigDecimal,
            ideal: BigDecimal,
            targetQuantity: BigDecimal,
            position: BigDecimal,
            fundingSource: FundingSource,
        ): FundingSell {
            val figures = CandidateFigures.of(quantity = quantity, price = price, ideal = ideal, custody = position)
            return FundingSell(
                ticker = ticker,
                quantity = quantity,
                price = price,
                amount = figures.amount,
                deficit = figures.deficit,
                ideal = ideal.defaultScale(),
                targetQuantity = targetQuantity,
                positionQuantity = position,
                fundingSource = fundingSource,
            )
        }
    }
}

// The two quote-derived figures every candidate carries, bundled so the factory never hands back an
// unnamed Pair.
private data class CandidateFigures(
    val amount: BigDecimal?,
    val deficit: BigDecimal?,
) {
    companion object {
        fun of(quantity: BigDecimal, price: BigDecimal?, ideal: BigDecimal, custody: BigDecimal) =
            price?.let {
                CandidateFigures(
                    amount = quantity.multiply(it).defaultScale(),
                    deficit = ideal.deficitAgainst(custody.multiply(it)),
                )
            } ?: CandidateFigures(amount = null, deficit = null)
    }
}

// Every listed ticker leaf, in chain order — the funding pass's slice input and the chain reads'
// shared traversal.
val OrderPlan.tickerSlices: List<TickerOrderPlan>
    get() = classes.filterIsInstance<EquitiesOrderPlan>().flatMap { it.strategies }.flatMap { it.tickers }

// The whole-unit Target a ticker's slices sum to — the line a funding Surplus is measured above.
val List<TickerOrderPlan>.targetUnits: BigDecimal
    get() = mapNotNull { it.targetQuantity }.sumOf { it }

// The kinds a Suggested Order can take (CONTEXT.md -> Suggested Order). The candidates phase
// produces BUY, NEW_ENTRY and EXIT; the funding pass adds SELL.
enum class OrderSide {
    BUY,
    SELL,
    NEW_ENTRY,
    EXIT,
    ;

    // The sides that spend money; the funding pass sums exactly these to size the shortfall.
    val isBuy: Boolean get() = this == BUY || this == NEW_ENTRY
}
