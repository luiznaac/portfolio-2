package dev.agner.portfolio.usecase.trade

import dev.agner.portfolio.usecase.trade.model.TradeCreation
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import org.springframework.stereotype.Service

@Service
class TradeService(
    private val repository: ITradeRepository,
) {

    suspend fun create(assetId: Int, creation: TradeCreation) = repository.save(assetId, creation)

    suspend fun fetchByAssetId(assetId: Int) = repository.fetchByAssetId(assetId)
}
