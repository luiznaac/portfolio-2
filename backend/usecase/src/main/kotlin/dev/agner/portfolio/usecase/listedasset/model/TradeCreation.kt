package dev.agner.portfolio.usecase.listedasset.model

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// No owner id: a trade is keyed by its ticker, not by a parent listed-asset row, so the creation
// carries no id the caller would have to fake (`assetId = 0`). The service call is
// `tradeService.createManual(creation)` — or `reconcile`, on the import path.
data class TradeCreation(
    val ticker: String,
    val side: TradeSide,
    val date: LocalDate,
    val venue: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val institutionId: Int,
    val fee: BigDecimal? = null,
) {
    init {
        // Direction is `side`; the quantity is always positive, so the repository can fold it into
        // the stored sign without a zero or a double-negative slipping through.
        require(quantity.signum() > 0) { "Trade quantity must be positive but was $quantity" }
    }

    // The natural tuple this creation reconciles by. The ticker is normalized here so a fractional
    // key (ALUP11F) matches the round-lot row (ALUP11) it is the same paper as.
    fun naturalKey(): TradeKey = TradeKey(
        date = date,
        side = side,
        venue = venue,
        ticker = ticker.normalizedTicker(),
        quantity = quantity,
        unitPrice = unitPrice,
        institutionId = institutionId,
    )
}
