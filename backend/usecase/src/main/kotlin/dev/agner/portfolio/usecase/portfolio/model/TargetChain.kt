package dev.agner.portfolio.usecase.portfolio.model

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import java.math.BigDecimal

// The ideal chain at every rung — Capital → class → strategy/sub-class → ticker — with each rung's
// Target and its Ideal in R$. It is the arithmetic both the allocation tree and the order plan
// compose; each adds its own Actuals, units or candidates on top.
data class TargetChain(
    val classes: List<ClassChain>,
)

// The class rung has two shapes, so it is a variant rather than two lists with one always empty:
// ACOES/FIIS carry strategies and their tickers, RENDA_FIXA carries sub-classes.
sealed interface ClassChain {
    val assetClass: AssetClass
    val target: BigDecimal?
    val ideal: BigDecimal
}

data class EquitiesChain(
    override val assetClass: AssetClass,
    override val target: BigDecimal?,
    override val ideal: BigDecimal,
    val strategies: List<StrategyChain>,
) : ClassChain

data class FixedIncomeChain(
    override val target: BigDecimal?,
    override val ideal: BigDecimal,
    val subClasses: List<SubClassChain>,
) : ClassChain {
    override val assetClass: AssetClass = AssetClass.RENDA_FIXA
}

data class StrategyChain(
    val strategy: String,
    val target: BigDecimal,
    val ideal: BigDecimal,
    val tickers: List<TickerChain>,
)

data class TickerChain(
    val ticker: String,
    val target: BigDecimal,
    val ideal: BigDecimal,
)

data class SubClassChain(
    val subClass: FixedIncomeSubClass,
    val target: BigDecimal?,
    val ideal: BigDecimal,
)
