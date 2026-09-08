package dev.agner.portfolio.usecase.trade

import dev.agner.portfolio.usecase.trade.model.TradeCreation
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import org.springframework.stereotype.Service

@Service
class TradeService(
    private val repository: ITradeRepository,
) {

    suspend fun create(creation: TradeCreation) = repository.save(creation)

    suspend fun fetchByAssetId(assetId: Int) = repository.fetchByAssetId(assetId)
}
