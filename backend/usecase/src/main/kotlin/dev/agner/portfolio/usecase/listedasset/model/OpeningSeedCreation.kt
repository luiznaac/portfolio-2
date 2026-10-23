package dev.agner.portfolio.usecase.listedasset.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// An adoption seed: the custody a ticker held at the start of the tracked window, entered as a buy at
// its closing price. It is created as an OPENING trade and retired by the history backfill once the
// imported history covers it. No owner id and no side — a seed is always a buy.
data class OpeningSeedCreation(
    val ticker: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val date: LocalDate,
    val venue: String = "VISTA",
) {
    init {
        require(quantity.signum() > 0) { "Opening seed quantity must be positive but was $quantity" }
        require(unitPrice.signum() >= 0) { "Opening seed price must not be negative but was $unitPrice" }
    }
}
