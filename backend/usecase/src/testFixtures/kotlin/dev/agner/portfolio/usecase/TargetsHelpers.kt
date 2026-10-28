package dev.agner.portfolio.usecase

import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.fixedincome.model.FixedIncomeSubClass
import dev.agner.portfolio.usecase.portfolio.model.ClassTarget
import dev.agner.portfolio.usecase.portfolio.model.StrategyTarget
import dev.agner.portfolio.usecase.portfolio.model.SubClassTarget
import dev.agner.portfolio.usecase.portfolio.model.TickerTarget
import java.math.BigDecimal

// The target chain's rungs, shared by the allocation and order-plan tests so a percentage's shape
// is authored once. Percentages are the number itself (50 is half), matching Targets.
fun classTarget(assetClass: AssetClass, percentage: String) =
    ClassTarget(assetClass, BigDecimal(percentage))

fun strategyTarget(assetClass: AssetClass, strategy: String, percentage: String) =
    StrategyTarget(assetClass, strategy, BigDecimal(percentage))

fun subClassTarget(subClass: FixedIncomeSubClass, percentage: String) =
    SubClassTarget(subClass, BigDecimal(percentage))

fun tickerTarget(strategy: String, ticker: String, percentage: String) =
    TickerTarget(strategy, ticker, BigDecimal(percentage))
