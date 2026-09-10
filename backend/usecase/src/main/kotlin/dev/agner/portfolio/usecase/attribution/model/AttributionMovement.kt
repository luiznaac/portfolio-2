package dev.agner.portfolio.usecase.attribution.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class AttributionReason {
    BUY,
    SELL,
    TRANSFER,
    ADJUSTMENT,
}

// Attribution is data the user enters, never derived — see AttributionService. Stored as signed
// movements (not a balance) so each strategy's history can be reconstructed, the same replay
// principle as Trade/AveragePriceCalculator.
data class AttributionMovement(
    val id: Int,
    val listedAssetId: Int,
    val strategyId: Int,
    val date: LocalDate,
    val quantity: BigDecimal,
    val reason: AttributionReason,
    val note: String? = null,
)

/**
 * What the client sends to record a movement. The owning asset is *not* part of this shape — it
 * comes from the URL path and is passed alongside it to
 * [dev.agner.portfolio.usecase.attribution.AttributionService.recordMovement].
 */
data class AttributionMovementCreation(
    val strategyId: Int,
    val date: LocalDate,
    val quantity: BigDecimal,
    val reason: AttributionReason,
    val note: String? = null,
)
