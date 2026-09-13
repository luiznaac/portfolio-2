package dev.agner.portfolio.usecase.listedasset.consolidation

import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.listedasset.ListedAssetService
import dev.agner.portfolio.usecase.listedasset.consolidation.model.ListedAssetConsolidationContext
import dev.agner.portfolio.usecase.trade.TradeService
import org.springframework.stereotype.Component

/**
 * Builds the ledger a [ListedAssetConsolidator] replays: the asset itself plus its trades and
 * corporate actions.
 */
@Component
class ListedAssetConsolidationContextProvider(
    private val listedAssetService: ListedAssetService,
    private val tradeService: TradeService,
    private val corporateActionService: CorporateActionService,
) {

    suspend fun fetchConsolidatableIds() = listedAssetService.fetchAll().map { it.id }

    suspend fun buildContext(productId: Int): ListedAssetConsolidationContext {
        val asset = listedAssetService.fetchById(productId)

        return ListedAssetConsolidationContext(
            asset = asset,
            trades = tradeService.fetchByAssetId(productId),
            corporateActions = corporateActionService.fetchByAssetId(productId),
        )
    }
}
