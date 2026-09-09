package dev.agner.portfolio.usecase.allocation.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// capital = externalBalance + plannedContribution — both still manual input (see decision 2 in
// the plan: fixed income stays a mirror until Fase 6). Kept with history so it can chart later,
// same shape as IndexValue/ListedAssetPosition.
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
