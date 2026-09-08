package dev.agner.portfolio.usecase.trade.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// assetId defaults to 0 because it's always overwritten from the URL path by the controller
// (POST /listed-assets/{listed_asset_id}/trades) — the frontend never sends it.
data class TradeCreation(
    val assetId: Int = 0,
    val date: LocalDate,
    val quantity: BigDecimal,
    val price: BigDecimal,
)
