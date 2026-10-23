package dev.agner.portfolio.usecase.listedasset.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// A marked reconciliation record for a custody quantity delta the ledger cannot explain: a buy or a
// sell that falls outside the exported window, a missed corporate action, an unrecorded movement.
// It is a two-variant type, not a side plus a nullable price: a missing buy always enters the replay
// at a placeholder unit price (the market close), while a missing sale's proceeds are unknown until
// the user prices it.
//
// A GAP is not a Trade: it never feeds the tax ledger. It keeps the Position replay truthful while
// the true record is missing, and a true trade of the same ticker, side and quantity retires it.
sealed class Gap(
    open val id: Int,
    open val ticker: String,
    open val date: LocalDate,
    open val quantity: BigDecimal,
    open val status: GapStatus,
    // The highest trade/corporate-action id the ledger held when the GAP was created. Only a record
    // newer than the watermark can be the one that fills this gap; a pre-existing same-size trade
    // must not retire it.
    open val tradeWatermark: Int,
    open val actionWatermark: Int,
) {

    // A missing buy. Its cost is a market placeholder, so the average price it produces is
    // approximate until a true record replaces it.
    data class Buy(
        override val id: Int,
        override val ticker: String,
        override val date: LocalDate,
        override val quantity: BigDecimal,
        val unitPrice: BigDecimal,
        override val status: GapStatus,
        override val tradeWatermark: Int = 0,
        override val actionWatermark: Int = 0,
    ) : Gap(id, ticker, date, quantity, status, tradeWatermark, actionWatermark) {
        init { require(unitPrice.signum() >= 0) { "GAP unit price must not be negative but was $unitPrice" } }
    }

    // A missing sale. `unitPrice` is null while the proceeds are unknown, so the period's realized
    // result stays pending; pricing it fills the value without retiring the record.
    data class Sell(
        override val id: Int,
        override val ticker: String,
        override val date: LocalDate,
        override val quantity: BigDecimal,
        val unitPrice: BigDecimal?,
        override val status: GapStatus,
        override val tradeWatermark: Int = 0,
        override val actionWatermark: Int = 0,
    ) : Gap(id, ticker, date, quantity, status, tradeWatermark, actionWatermark) {
        val realizedPending: Boolean
            get() = unitPrice == null && status == GapStatus.OPEN
    }
}

val Gap.side: TradeSide
    get() = if (this is Gap.Buy) TradeSide.BUY else TradeSide.SELL

val Gap.averageApproximate: Boolean
    get() = this is Gap.Buy && status == GapStatus.OPEN

enum class GapStatus {
    OPEN,
    REPLACED,
}

// A record to create, without the id the store assigns. The variant states whether this is a missing
// buy (with its placeholder price) or a missing sale (unpriced until the owner knows the proceeds).
sealed interface GapCreation {
    val ticker: String
    val date: LocalDate
    val quantity: BigDecimal

    data class MissingBuy(
        override val ticker: String,
        override val date: LocalDate,
        override val quantity: BigDecimal,
        val unitPrice: BigDecimal,
        val tradeWatermark: Int = 0,
        val actionWatermark: Int = 0,
    ) : GapCreation

    data class MissingSale(
        override val ticker: String,
        override val date: LocalDate,
        override val quantity: BigDecimal,
        val tradeWatermark: Int = 0,
        val actionWatermark: Int = 0,
    ) : GapCreation
}

// The one number a user can edit on a GAP: a missing sale's proceeds. A missing buy's placeholder is
// replaced by the true trade, not priced.
data class GapPriceUpdate(val unitPrice: BigDecimal)
