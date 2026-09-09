package dev.agner.portfolio.usecase.allocation.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// Versioned by effectiveFrom: changing the policy is a dated fact, never an overwrite — retiring
// an old target keeps its effectiveFrom, a new one takes over from its own date forward.
data class AssetClassTarget(
    val id: Int,
    val assetClass: AssetClass,
    val weight: BigDecimal,
    val effectiveFrom: LocalDate,
)

data class AssetClassTargetCreation(
    val assetClass: AssetClass,
    val weight: BigDecimal,
    val effectiveFrom: LocalDate,
)
