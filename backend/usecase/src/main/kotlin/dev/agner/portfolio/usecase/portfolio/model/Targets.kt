package dev.agner.portfolio.usecase.portfolio.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import java.math.BigDecimal

// Target percentages are the percentage number — 50.00 is half — matching the Bond rate convention.
// The engine divides by 100 when it multiplies the chain.
//
// A class's share of Capital — the chain's first rung.
data class ClassTarget(
    val assetClass: AssetClass,
    val percentage: BigDecimal,
)

// A strategy's share of its class — the second rung of the ACOES/FIIS branch. The strategy is
// referenced by its name until the Strategy entity lands (CONTEXT.md -> Strategy); once strategies
// are first-class this reference becomes an id without changing the chain's shape.
data class StrategyTarget(
    val assetClass: AssetClass,
    val strategy: String,
    val percentage: BigDecimal,
)

// The RF sub-class share, the second rung of the RENDA_FIXA branch.
data class SubClassTarget(
    val subClass: FixedIncomeSubClass,
    val percentage: BigDecimal,
)

// A ticker's weight inside its strategy — the chain's leaf.
data class TickerTarget(
    val strategy: String,
    val ticker: String,
    val percentage: BigDecimal,
)

// The month's whole target chain, grouped by rung. Grouping (rather than a flat list of rows
// carrying a level discriminator) keeps the edge contract symmetric — the shape an entry form posts
// is the shape the read returns — and gives each rung its own key fields instead of a set of
// nullable ones. The single stored `target` table still carries the TargetLevel discriminator; the
// repository owns that translation.
data class Targets(
    val classes: List<ClassTarget>,
    val strategies: List<StrategyTarget>,
    val subClasses: List<SubClassTarget>,
    val tickers: List<TickerTarget>,
) {
    // Every ticker crossing the domain boundary is canonicalised (see normalizedTicker). A target
    // chain arrives from an edge payload, so it normalises its tickers on the way in, exactly as a
    // TradeCreation does — the fractional-market `F` suffix is stripped once, here.
    fun normalized(): Targets = copy(
        tickers = tickers.map { it.copy(ticker = it.ticker.normalizedTicker()) },
    )
}
