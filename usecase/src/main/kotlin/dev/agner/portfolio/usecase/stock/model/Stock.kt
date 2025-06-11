package dev.agner.portfolio.usecase.stock.model

import java.math.BigDecimal
import java.time.LocalDate

data class Stock(
    val symbol: String,
    val name: String,
    val prices: List<StockPrice>,
)

data class StockPrice(
    val price: BigDecimal,
    val currency: String,
    val date: LocalDate,
)
