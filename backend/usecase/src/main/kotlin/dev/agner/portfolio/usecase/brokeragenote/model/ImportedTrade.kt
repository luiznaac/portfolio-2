package dev.agner.portfolio.usecase.brokeragenote.model

import dev.agner.portfolio.usecase.trade.model.TradeSide
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * One parsed statement row, staged for review — nothing here is persisted yet. [listedAssetId] is
 * null when the ticker couldn't be resolved to a registered asset. [matchesPlan] flags a row whose
 * side and ticker line up with the current order plan so the UI can highlight it; it is
 * informational and never blocks confirmation.
 */
data class ImportedTrade(
    val ticker: String,
    val listedAssetId: Int?,
    val date: LocalDate,
    val side: TradeSide,
    val quantity: BigDecimal,
    val price: BigDecimal,
    val notional: BigDecimal,
    val resolvable: Boolean,
    val matchesPlan: Boolean,
)

data class ImportPreview(
    val trades: List<ImportedTrade>,
    val unresolvedTickers: List<String>,
)

/** What the user approved after reviewing the preview — the only shape that becomes a real trade. */
data class ImportedTradeConfirmation(
    val ticker: String,
    val date: LocalDate,
    val side: TradeSide,
    val quantity: BigDecimal,
    val price: BigDecimal,
)
