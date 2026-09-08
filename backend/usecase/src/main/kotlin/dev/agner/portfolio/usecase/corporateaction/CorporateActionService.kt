package dev.agner.portfolio.usecase.corporateaction

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation.TickerChangeCreation
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.ListedAssetService
import org.springframework.stereotype.Service

@Service
class CorporateActionService(
    private val repository: ICorporateActionRepository,
    private val listedAssetService: ListedAssetService,
) {

    suspend fun create(creation: CorporateActionCreation) = repository.save(creation).also {
        // A ticker change is dual-natured: it's an event in the asset's timeline (for replay) AND
        // it updates what the asset is currently called (for display, new trades, gateway lookups).
        if (creation is TickerChangeCreation) {
            listedAssetService.changeTicker(creation.assetId, creation.newTicker, creation.date)
        }
    }

    suspend fun fetchByAssetId(assetId: Int) = repository.fetchByAssetId(assetId)
}
