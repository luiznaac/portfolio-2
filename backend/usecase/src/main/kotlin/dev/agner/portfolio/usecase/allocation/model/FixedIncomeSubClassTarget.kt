package dev.agner.portfolio.usecase.allocation.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// Weight within RENDA_FIXA (Pós/Pré/Inflação), versioned the same way as AssetClassTarget.
data class FixedIncomeSubClassTarget(
    val id: Int,
    val subClass: FixedIncomeSubClass,
    val weight: BigDecimal,
    val effectiveFrom: LocalDate,
)

data class FixedIncomeSubClassTargetCreation(
    val subClass: FixedIncomeSubClass,
    val weight: BigDecimal,
    val effectiveFrom: LocalDate,
)
