package dev.agner.portfolio.usecase.bond.consolidation.model

import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Buy
import dev.agner.portfolio.usecase.bond.model.BondOrder.DownToZero.FullRedemption
import dev.agner.portfolio.usecase.bond.model.BondOrder.Redemption.Sell

data class BondConsolidationContext(
    val buys: List<Buy>,
    val sells: List<Sell>,
    val fullRedemption: FullRedemption?,
)
