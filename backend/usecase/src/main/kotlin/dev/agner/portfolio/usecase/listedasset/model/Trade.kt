package dev.agner.portfolio.usecase.listedasset.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class TradeSide {
    BUY,
    SELL,
}

// A single execution in the listed-asset ledger. Direction is a sealed subtype, not a signed
// quantity: `quantity` is always positive and the side carries the direction, so a zero quantity
// can never be silently read as a sell. The `trade` table keeps the direction as a signed
// `quantity` column; `TradeTable.toModel` is the one place that translates.
sealed class Trade(
    open val id: Int,
    open val ticker: String,
    open val date: LocalDate,
    open val venue: String,
    open val quantity: BigDecimal,
    open val unitPrice: BigDecimal,
    open val institutionId: Int,
    open val fee: BigDecimal?,
) {
    data class Buy(
        override val id: Int,
        override val ticker: String,
        override val date: LocalDate,
        override val venue: String,
        override val quantity: BigDecimal,
        override val unitPrice: BigDecimal,
        override val institutionId: Int,
        override val fee: BigDecimal?,
    ) : Trade(id, ticker, date, venue, quantity, unitPrice, institutionId, fee) {
        // Checked in the subtype, not the base: an `init` in the base would read the overridden
        // `quantity` before the subtype's field is assigned.
        init { requirePositiveQuantity(quantity) }
    }

    data class Sell(
        override val id: Int,
        override val ticker: String,
        override val date: LocalDate,
        override val venue: String,
        override val quantity: BigDecimal,
        override val unitPrice: BigDecimal,
        override val institutionId: Int,
        override val fee: BigDecimal?,
    ) : Trade(id, ticker, date, venue, quantity, unitPrice, institutionId, fee) {
        init { requirePositiveQuantity(quantity) }
    }
}

private fun requirePositiveQuantity(quantity: BigDecimal) {
    require(quantity.signum() > 0) { "Trade quantity must be positive but was $quantity" }
}
