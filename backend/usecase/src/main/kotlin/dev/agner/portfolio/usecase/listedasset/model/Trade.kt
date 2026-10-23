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
//
// `source` records where the row came from, `confirmedByB3` marks a manual trade an import matched
// (its edits are kept), and `ordinal` is the 1-based occurrence of this trade's natural tuple in the
// ledger — the tuple alone is not unique, since the same execution can repeat legitimately.
sealed class Trade(
    open val id: Int,
    open val ticker: String,
    open val date: LocalDate,
    open val venue: String,
    open val quantity: BigDecimal,
    open val unitPrice: BigDecimal,
    open val institutionId: Int,
    open val fee: BigDecimal?,
    open val source: TradeSource,
    open val confirmedByB3: Boolean,
    open val ordinal: Int,
    // How much of an OPENING seed the backfill has already absorbed. Only seeds use it; a regular
    // trade stays at zero. Keeping it lets absorb rerun without subtracting the same history twice.
    open val absorbedQuantity: BigDecimal,
) {
    // The direction as an explicit property, so it survives serialization (the API's GET /trades
    // would otherwise return a trade with no way to tell a buy from a sell).
    abstract val side: TradeSide

    data class Buy(
        override val id: Int,
        override val ticker: String,
        override val date: LocalDate,
        override val venue: String,
        override val quantity: BigDecimal,
        override val unitPrice: BigDecimal,
        override val institutionId: Int,
        override val fee: BigDecimal?,
        override val source: TradeSource,
        override val confirmedByB3: Boolean,
        override val ordinal: Int,
        override val absorbedQuantity: BigDecimal = BigDecimal.ZERO,
    ) : Trade(
        id,
        ticker,
        date,
        venue,
        quantity,
        unitPrice,
        institutionId,
        fee,
        source,
        confirmedByB3,
        ordinal,
        absorbedQuantity,
    ) {
        override val side: TradeSide = TradeSide.BUY

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
        override val source: TradeSource,
        override val confirmedByB3: Boolean,
        override val ordinal: Int,
        override val absorbedQuantity: BigDecimal = BigDecimal.ZERO,
    ) : Trade(
        id,
        ticker,
        date,
        venue,
        quantity,
        unitPrice,
        institutionId,
        fee,
        source,
        confirmedByB3,
        ordinal,
        absorbedQuantity,
    ) {
        override val side: TradeSide = TradeSide.SELL
        init { requirePositiveQuantity(quantity) }
    }
}

// Whether the trade's execution is on the ledger through a B3 import — directly, or because the
// import later confirmed the manual record. The one definition the order lifecycle reads.
val Trade.isImported: Boolean get() = source == TradeSource.B3_IMPORT || confirmedByB3

private fun requirePositiveQuantity(quantity: BigDecimal) {
    require(quantity.signum() > 0) { "Trade quantity must be positive but was $quantity" }
}
