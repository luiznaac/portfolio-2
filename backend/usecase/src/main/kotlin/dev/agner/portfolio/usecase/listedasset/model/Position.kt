package dev.agner.portfolio.usecase.listedasset.model

import java.math.BigDecimal

// The stored projection of a listed asset's custody: the quantity and the fiscal average price the
// trade ledger currently reconstructs. It is derived, never typed in — every trade insert replays
// the ticker's ledger into it, and `PositionService.reproject` repairs any drift the same way.
// `averagePrice` is the Brazilian "preço médio" (weighted mean unit cost).
data class Position(
    val ticker: String,
    val quantity: BigDecimal,
    val averagePrice: BigDecimal,
) {
    companion object {
        fun empty(ticker: String) = Position(ticker, BigDecimal.ZERO, BigDecimal.ZERO)
    }
}
