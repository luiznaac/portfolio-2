package dev.agner.portfolio.usecase.bond.consolidation.model

import dev.agner.portfolio.usecase.bond.consolidation.model.BondConsolidationContext.RedemptionContext
import dev.agner.portfolio.usecase.bond.model.BondOrderStatementCreation
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

data class BondConsolidationResult(
    val principal: BigDecimal,
    val yieldAmount: BigDecimal,
    val remainingSells: Map<LocalDate, RedemptionContext>,
    val statements: List<BondOrderStatementCreation>,
)
