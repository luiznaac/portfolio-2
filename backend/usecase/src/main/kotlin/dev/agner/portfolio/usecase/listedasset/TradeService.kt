package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import org.springframework.stereotype.Service

@Service
class TradeService(
    private val repository: ITradeRepository,
) {

    // The ticker is the ledger's canonical key, so it is normalized at this boundary (ALUP11F and
    // ALUP11 are the same paper) before the repository persists it.
    suspend fun create(creation: TradeCreation): Trade =
        repository.save(creation.copy(ticker = creation.ticker.normalizedTicker()))

    suspend fun fetchAll() = repository.fetchAll()

    suspend fun fetchAllBy(ticker: String) = repository.fetchAllBy(ticker.normalizedTicker())
}
