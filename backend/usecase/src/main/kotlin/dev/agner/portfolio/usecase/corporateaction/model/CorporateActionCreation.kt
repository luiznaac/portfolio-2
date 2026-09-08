package dev.agner.portfolio.usecase.corporateaction.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

sealed class CorporateActionCreation(
    open val assetId: Int,
    open val date: LocalDate,
) {
    // assetId defaults to 0 because it's always overwritten from the URL path by the controller
    // (POST /listed-assets/{listed_asset_id}/corporate-actions/...) — the frontend never sends it.
    data class SplitCreation(
        override val assetId: Int = 0,
        override val date: LocalDate,
        val ratio: BigDecimal,
    ) : CorporateActionCreation(assetId, date)

    data class ReverseSplitCreation(
        override val assetId: Int = 0,
        override val date: LocalDate,
        val ratio: BigDecimal,
    ) : CorporateActionCreation(assetId, date)

    data class BonusCreation(
        override val assetId: Int = 0,
        override val date: LocalDate,
        val ratio: BigDecimal,
        val valuePerNewShare: BigDecimal,
    ) : CorporateActionCreation(assetId, date)

    data class TickerChangeCreation(
        override val assetId: Int = 0,
        override val date: LocalDate,
        val newTicker: String,
    ) : CorporateActionCreation(assetId, date)
}
