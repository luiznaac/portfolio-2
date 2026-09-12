package dev.agner.portfolio.usecase.brokeragenote.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * One parsed statement row, staged for review — nothing here is persisted yet. [quantity] is
 * signed the same way [dev.agner.portfolio.usecase.trade.model.Trade] is (positive buy, negative
 * sell). [listedAssetId] is null when the ticker couldn't be resolved to a registered asset;
 * [matchesPlan] flags a row whose side/ticker lines up with the current [dev.agner.portfolio.usecase.order.model.OrderPlan]
 * so the UI can highlight it — informational only, never blocks confirmation.
 */
data class ImportedTrade(
    val ticker: String,
    val listedAssetId: Int?,
    val date: LocalDate,
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

/** What the user actually approved after reviewing the preview — the only shape that becomes real [dev.agner.portfolio.usecase.trade.model.Trade]s. */
data class ImportedTradeConfirmation(
    val ticker: String,
    val date: LocalDate,
    val quantity: BigDecimal,
    val price: BigDecimal,
)
