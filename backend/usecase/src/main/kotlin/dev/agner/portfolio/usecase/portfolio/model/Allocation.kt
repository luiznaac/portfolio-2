package dev.agner.portfolio.usecase.portfolio.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// The computed allocation of the open month: the whole Capital → class → strategy/sub-class → ticker
// chain, each rung carrying its target, Ideal, Actual, Deficit and Drift. Computed on read and never
// stored, so it can never drift from the positions, quotes and target chain it composes.
//
// Ideal is Capital × the chain (class % × strategy/sub-class share × ticker weight); Actual is the RF
// consolidation engine's product values plus Position × current quote for the listed book. Deficit is
// Ideal − Actual (positive = short); Drift is (Actual − Ideal) ÷ Capital, in percentage points.
//
// The read is month-free: with no open month there is no chain to scale, so `period` is absent and the
// tree carries Actual only. The bucket, outside and unclassified rows stay live because they come from
// custody and attribution, not from the month.
//
// The listed tree is additive: a class's report Strategy slices, its Manual Strategy (bucket) and its
// outside row sum to the class Actual, which stays full custody. `unattributed` is the custody inside
// no Strategy — outside plus unclassified — so bucket units never count as unattributed.
//
// `actual` is the priced sum only: a held ticker with no stored close is unknown, never zero. It is
// carried in `unpriced` (ticker + quantity), excluded from every priced sum, and `partial` tells the
// UI that the totals cover only the priced part of the book. `negative` is the custody flagged for
// repair, with its priced value still counted.
data class Allocation(
    val period: YearMonth?,
    val capital: BigDecimal,
    val floor: BigDecimal,
    val ideal: BigDecimal,
    val actual: BigDecimal,
    val deficit: BigDecimal,
    val drift: BigDecimal,
    val unattributed: BigDecimal,
    val partial: Boolean = false,
    val unpriced: List<HeldTicker> = emptyList(),
    val negative: List<HeldTicker> = emptyList(),
    val unclassified: UnclassifiedAllocation,
    val classes: List<ClassAllocation>,
)

// A held ticker with a data-quality problem: no close at all (unpriced) or negative custody. Quantity
// is the signed custody the book holds, so the panel can show how much is unresolved.
data class HeldTicker(
    val ticker: String,
    val quantity: BigDecimal,
)

// One class rung. ACOES/FIIS carry `strategies` (report-driven), an optional `manual` bucket and the
// `outside` remainder; RENDA_FIXA carries `subClasses` (RF targets at sub-class level only, so the RF
// branch has no ticker rung). `target` is null when the month's chain carries no percentage for the
// class.
data class ClassAllocation(
    val assetClass: AssetClass,
    val target: BigDecimal?,
    val ideal: BigDecimal,
    val actual: BigDecimal,
    val deficit: BigDecimal,
    val drift: BigDecimal,
    // The class's priced Actual excludes at least one held ticker with no close.
    val partial: Boolean = false,
    // The part of this class's Actual inside no Strategy (its outside custody).
    val unattributed: BigDecimal,
    val strategies: List<StrategyAllocation>,
    val manual: ManualAllocation?,
    val outside: OutsideAllocation,
    val subClasses: List<SubClassAllocation>,
)

// A report Strategy rung — the second rung of an ACOES/FIIS class. Its Actual is the attributed
// units of its target tickers × the current quote.
data class StrategyAllocation(
    val strategy: String,
    val target: BigDecimal,
    val ideal: BigDecimal,
    val actual: BigDecimal,
    val deficit: BigDecimal,
    val drift: BigDecimal,
    // The strategy's priced Actual excludes at least one attributed unpriced leaf.
    val partial: Boolean = false,
    val tickers: List<TickerAllocation>,
)

// The Manual Strategy (bucket) rung: Actual only, so Alvo, Ideal, Δ R$ and Δ p.p. are absent rather
// than zero — a zero would draw an excess bar for money that has no target.
data class ManualAllocation(
    val strategy: String,
    val actual: BigDecimal,
    val tickers: List<ActualTickerAllocation>,
)

// The class's outside custody, after the bucket, so the class's rows sum to its Actual. Actual only,
// for the same reason the bucket carries no target.
data class OutsideAllocation(
    val actual: BigDecimal,
    val tickers: List<ActualTickerAllocation>,
)

// A held ticker whose catalog class is null: a data-quality state, not a planning one. It belongs to
// no class or Strategy rung but still counts in the Portfolio total.
data class UnclassifiedAllocation(
    val actual: BigDecimal,
    val tickers: List<ActualTickerAllocation>,
)

// A leaf with only an Actual — a bucket, outside or unclassified ticker. `actual` is null when the
// ticker has no close (unknown, rendered "—"); `quantity` is the units the leaf names so the rule
// "actual = quantity × price" stays visible, and `stale`/`quoteDate` flag an old-but-real close.
data class ActualTickerAllocation(
    val ticker: String,
    val actual: BigDecimal?,
    val quantity: BigDecimal = BigDecimal.ZERO,
    val price: BigDecimal? = null,
    val quoteDate: LocalDate? = null,
    val stale: Boolean = false,
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

// A ticker leaf — the chain's last rung, under a report Strategy. Actual, deficit and drift are null
// when the leaf's ticker has no close: the plan knows the target, but the book's value is unknown.
data class TickerAllocation(
    val ticker: String,
    val quantity: BigDecimal = BigDecimal.ZERO,
    val target: BigDecimal,
    val ideal: BigDecimal,
    val actual: BigDecimal?,
    val deficit: BigDecimal?,
    val drift: BigDecimal?,
    val price: BigDecimal? = null,
    val quoteDate: LocalDate? = null,
    val stale: Boolean = false,
)
