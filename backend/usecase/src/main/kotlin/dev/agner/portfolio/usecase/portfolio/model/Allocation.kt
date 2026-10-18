package dev.agner.portfolio.usecase.portfolio.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The computed allocation of the open month: the whole Capital → class → strategy/sub-class → ticker
// chain, each rung carrying its target, Ideal, Actual, Deficit and Drift. Computed on read and never
// stored, so it can never drift from the positions, quotes and target chain it composes.
//
// Ideal is Capital × the chain (class % × strategy/sub-class share × ticker weight); Actual is the RF
// consolidation engine's product values plus Position × current quote for the listed book. Deficit is
// Ideal − Actual (positive = short); Drift is (Actual − Ideal) ÷ Capital, in percentage points.
data class Allocation(
    val period: YearMonth,
    val capital: BigDecimal,
    val floor: BigDecimal,
    val ideal: BigDecimal,
    val actual: BigDecimal,
    val deficit: BigDecimal,
    val drift: BigDecimal,
    // The listed value no strategy targets — the reconciliation bar's hatched segment. Until the
    // stored Attribution ledger lands, the tree derives strategy ownership from the target chain, so
    // a held ticker the chain does not name is unattributed.
    val unattributed: BigDecimal,
    val classes: List<ClassAllocation>,
)

// One class rung. ACOES/FIIS carry `strategies`, RENDA_FIXA carries `subClasses` (RF targets at
// sub-class level only, so the RF branch has no ticker rung). `target` is null when the month's chain
// carries no percentage for the class.
data class ClassAllocation(
    val assetClass: AssetClass,
    val target: BigDecimal?,
    val ideal: BigDecimal,
    val actual: BigDecimal,
    val deficit: BigDecimal,
    val drift: BigDecimal,
    // The part of this class's Actual not attributed to any strategy (its listed holdings).
    val unattributed: BigDecimal,
    val strategies: List<StrategyAllocation>,
    val subClasses: List<SubClassAllocation>,
)

// A strategy rung — the second rung of an ACOES/FIIS class. The strategy is a name until the Strategy
// entity lands (CONTEXT.md → Strategy).
data class StrategyAllocation(
    val strategy: String,
    val target: BigDecimal,
    val ideal: BigDecimal,
    val actual: BigDecimal,
    val deficit: BigDecimal,
    val drift: BigDecimal,
    val tickers: List<TickerAllocation>,
)

// The RF sub-class rung — the second and last rung of RENDA_FIXA. All three sub-classes are always
// present (the fixed rung of the entry form), so `target` is null when the chain declares none.
data class SubClassAllocation(
    val subClass: FixedIncomeSubClass,
    val target: BigDecimal?,
    val ideal: BigDecimal,
    val actual: BigDecimal,
    val deficit: BigDecimal,
    val drift: BigDecimal,
)

// A ticker leaf — the chain's last rung, under a strategy.
data class TickerAllocation(
    val ticker: String,
    val target: BigDecimal,
    val ideal: BigDecimal,
    val actual: BigDecimal,
    val deficit: BigDecimal,
    val drift: BigDecimal,
)
