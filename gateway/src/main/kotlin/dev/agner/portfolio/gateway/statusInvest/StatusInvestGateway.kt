package dev.agner.portfolio.gateway.statusInvest

import dev.agner.portfolio.gateway.configuration.StatusInvestClient
import dev.agner.portfolio.usecase.stock.gateway.StockPriceGateway
import dev.agner.portfolio.usecase.stock.model.StockPrice
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody
import java.time.LocalDate

@Component
class StatusInvestGateway(
    @StatusInvestClient private val webClient: WebClient,
) : StockPriceGateway {

    override suspend fun getStockPrice(symbol: String, dateFrom: LocalDate, dateTo: LocalDate) =
        webClient
            .get()
            .uri("/api/v1/status/invest")
            .header("","")
            .retrieve()
            .awaitBody<List<StockPrice>>()
}
