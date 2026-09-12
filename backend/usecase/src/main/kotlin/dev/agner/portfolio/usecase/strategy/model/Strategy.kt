package dev.agner.portfolio.usecase.strategy.model

import dev.agner.portfolio.usecase.allocation.model.AssetClass

// assetClass says which class's ideal capital this strategy draws from (see StrategyWeight) —
// a stock strategy like "Top" lives under ACOES, "Fundamentalista FII" under REAL_STATE. A
// strategy belongs to exactly one class; it doesn't split across classes.
data class Strategy(
    val id: Int,
    val name: String,
    val assetClass: AssetClass,
)

data class StrategyCreation(
    val name: String,
    val assetClass: AssetClass,
)
