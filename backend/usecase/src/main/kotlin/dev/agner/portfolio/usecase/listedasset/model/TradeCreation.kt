package dev.agner.portfolio.usecase.listedasset.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// No owner id: a trade is keyed by its ticker, not by a parent listed-asset row, so the creation
// carries no id the caller would have to fake (`assetId = 0`). The service call is
// `tradeService.create(creation)`.
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
}
