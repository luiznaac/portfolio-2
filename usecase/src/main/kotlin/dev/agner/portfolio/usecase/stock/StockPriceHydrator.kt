package dev.agner.portfolio.usecase.stock

import dev.agner.portfolio.usecase.stock.gateway.StockPriceGateway
import dev.agner.portfolio.usecase.stock.model.StockPrice
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class StockPriceHydrator(
    private val stockPriceGateway: StockPriceGateway,
) {

    suspend fun hydrateStockPrices(symbol: String, dateFrom: LocalDate, dateTo: LocalDate): List<StockPrice> {
        return stockPriceGateway.getStockPrice(symbol, dateFrom, dateTo)
    }
}
