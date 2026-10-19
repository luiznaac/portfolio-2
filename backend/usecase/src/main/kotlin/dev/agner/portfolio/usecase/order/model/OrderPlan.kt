package dev.agner.portfolio.usecase.order.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The open month's computed order plan. The ideal chain is computed on read from (Capital, Floor,
// Targets, quotes) and is never stored; only the later lifecycle slice persists the orders it
// suggests. `investableCapital` is Capital minus the cash Floor — the amount every Ideal scales.
data class OrderPlan(
    val period: YearMonth,
    val phase: OrderPlanPhase,
    val capital: BigDecimal,
    val floor: BigDecimal,
    val investableCapital: BigDecimal,
    val classes: List<ClassOrderPlan>,
    val candidates: List<OrderCandidate>,
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
data class TickerOrderPlan(
    val ticker: String,
    val target: BigDecimal,
    val ideal: BigDecimal,
    val price: BigDecimal?,
    val targetQuantity: BigDecimal?,
)

// The RF sub-class rung — the second and last rung of RENDA_FIXA. All three sub-classes are always
// present, so `target` is null when the chain declares none.
data class SubClassOrderPlan(
    val subClass: FixedIncomeSubClass,
    val target: BigDecimal?,
    val ideal: BigDecimal,
)

// A leaf order the ideal phase suggests. `quantity` is whole units, floored both ways; the remainder
// stays uninvested. NEW_ENTRY opens a zero position; EXIT closes a dropped ticker's full
// position. A targeted surplus is held, so it never becomes a SELL here.
data class OrderCandidate(
    val ticker: String,
    val side: OrderSide,
    val quantity: BigDecimal,
    val price: BigDecimal? = null,
    val amount: BigDecimal? = null,
    val ideal: BigDecimal,
    val targetQuantity: BigDecimal,
    val positionQuantity: BigDecimal,
)

// The kinds a Suggested Order can take (CONTEXT.md -> Suggested Order). Only the ideal phase's
// candidates are produced today; SELL joins with the funding slice.
enum class OrderSide {
    BUY,
    SELL,
    NEW_ENTRY,
    EXIT,
}
