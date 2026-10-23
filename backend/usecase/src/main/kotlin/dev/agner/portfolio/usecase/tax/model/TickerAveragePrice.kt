package dev.agner.portfolio.usecase.tax.model

import java.math.BigDecimal

// The fiscal mean unit cost (preço médio) of one held ticker, replayed from its trades and
// corporate-action adjustments. Only a held position is worth a row: a ticker sold to zero has no
// basis left to report.
data class TickerAveragePrice(
    val ticker: String,
    val quantity: BigDecimal,
    val averagePrice: BigDecimal,
)
