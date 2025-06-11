package dev.agner.portfolio.usecase.stock.gateway

import dev.agner.portfolio.usecase.stock.model.StockPrice
import java.time.LocalDate

interface StockPriceGateway {
    suspend fun getStockPrice(symbol: String, dateFrom: LocalDate, dateTo: LocalDate): List<StockPrice>
}
