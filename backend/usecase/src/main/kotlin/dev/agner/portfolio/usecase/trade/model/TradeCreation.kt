package dev.agner.portfolio.usecase.trade.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * What the client sends to register a trade. The owning asset is *not* part of this shape — it
 * comes from the URL path and is passed alongside it to [dev.agner.portfolio.usecase.trade.TradeService.create].
 */
data class TradeCreation(
    val date: LocalDate,
    val side: TradeSide,
    val quantity: BigDecimal,
    val price: BigDecimal,
)
