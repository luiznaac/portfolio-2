package dev.agner.portfolio.usecase.trade

import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.model.TradeCreation
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

@Service
class TradeService(
    private val repository: ITradeRepository,
) {

    suspend fun create(assetId: Int, creation: TradeCreation): Trade {
        // The side is the type, never the sign: the repository negates a SELL onto a single signed
        // column, so a non-positive quantity would read back as the opposite side (or a zero sell).
        require(creation.quantity > BigDecimal.ZERO) {
            "Trade quantity must be positive, got ${creation.quantity}"
        }
        return repository.save(assetId, creation)
    }

    suspend fun fetchByAssetId(assetId: Int) = repository.fetchByAssetId(assetId)
}
