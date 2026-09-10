package dev.agner.portfolio.usecase.trade.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class TradeSide {
    BUY,
    SELL,
}

/**
 * A single execution. Which side it is, is the type — never the sign of [quantity], which is
 * always positive. Callers branch with an exhaustive `when (trade)` so a new side can't be
 * silently mishandled; use [signedQuantity] only where a running balance genuinely needs one
 * number.
 *
 * The `trade` table still stores one signed `quantity` column; [dev.agner.portfolio.persistence.trade.TradeRepository]
 * is the only place that translates between the two representations.
 */
sealed class Trade {
    abstract val id: Int
    abstract val assetId: Int
    abstract val date: LocalDate
    abstract val quantity: BigDecimal
    abstract val price: BigDecimal
    abstract val side: TradeSide

    val notional: BigDecimal get() = quantity * price

    val signedQuantity: BigDecimal get() = when (this) {
        is Buy -> quantity
        is Sell -> quantity.negate()
    }

    data class Buy(
        override val id: Int,
        override val assetId: Int,
        override val date: LocalDate,
        override val quantity: BigDecimal,
        override val price: BigDecimal,
    ) : Trade() {
        override val side = TradeSide.BUY
    }

    data class Sell(
        override val id: Int,
        override val assetId: Int,
        override val date: LocalDate,
        override val quantity: BigDecimal,
        override val price: BigDecimal,
    ) : Trade() {
        override val side = TradeSide.SELL
    }
}
