package dev.agner.portfolio.usecase.allocation.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// capital = externalBalance + plannedContribution, both still manual input. Kept with history so
// it can be charted later, the same shape as IndexValue/ListedAssetPosition.
data class CapitalSnapshot(
    val id: Int,
    val date: LocalDate,
    val externalBalance: BigDecimal,
    val plannedContribution: BigDecimal,
) {
    val total: BigDecimal get() = externalBalance + plannedContribution
}

data class CapitalSnapshotCreation(
    val date: LocalDate,
    val externalBalance: BigDecimal,
    val plannedContribution: BigDecimal,
)
