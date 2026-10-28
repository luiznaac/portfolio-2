package dev.agner.portfolio.usecase.listedasset.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.util.Objects

// A trade's natural tuple: date + side + venue + ticker + quantity + unit price + institution. Two
// trades with the same tuple describe the same execution seen twice (a manual entry and the B3 row
// that confirms it, or the same row across two overlapping exports), so the tuple — with its
// occurrence ordinal — is the reconciliation key.
//
// Not a `data class`: equality must be numeric, not scale-based. The stored quantity/unit_price are
// decimal(18,6), so a manual `100` and its stored read-back `100.000000` must be the same key, but
// `BigDecimal.equals` compares scale. `equals`/`hashCode` compare the stripped value instead.
class TradeKey(
    val date: LocalDate,
    val side: TradeSide,
    val venue: String,
    val ticker: String,
    val quantity: BigDecimal,
    val unitPrice: BigDecimal,
    val institutionId: Int,
) {
    override fun equals(other: Any?): Boolean =
        other is TradeKey &&
            date == other.date &&
            side == other.side &&
            venue == other.venue &&
            ticker == other.ticker &&
            institutionId == other.institutionId &&
            quantity.compareTo(other.quantity) == 0 &&
            unitPrice.compareTo(other.unitPrice) == 0

    override fun hashCode(): Int = Objects.hash(
        date,
        side,
        venue,
        ticker,
        institutionId,
        quantity.stripTrailingZeros(),
        unitPrice.stripTrailingZeros(),
    )

    override fun toString(): String =
        "TradeKey($ticker $side $venue $date $quantity @ $unitPrice institution=$institutionId)"
}
