package dev.agner.portfolio.usecase.attribution.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class AttributionReason {
    COMPRA,
    VENDA,
    TRANSFERENCIA,
    AJUSTE,
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

// listedAssetId defaults to 0 because it's always overwritten from the URL path by the
// controller (POST /listed-assets/{listed_asset_id}/attribution/movements) — the frontend never
// sends it, same convention as TradeCreation.
data class AttributionMovementCreation(
    val listedAssetId: Int = 0,
    val strategyId: Int,
    val date: LocalDate,
    val quantity: BigDecimal,
    val reason: AttributionReason,
    val note: String? = null,
)
