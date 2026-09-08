package dev.agner.portfolio.usecase.listedasset.position

import dev.agner.portfolio.usecase.listedasset.position.repository.IListedAssetPositionRepository
import org.springframework.stereotype.Service

@Service
class ListedAssetPositionService(
    private val repository: IListedAssetPositionRepository,
) {

    suspend fun getByAssetId(assetId: Int) = repository.fetchByAssetId(assetId)

    suspend fun getLastByAssetId(assetId: Int) = getByAssetId(assetId).last()
}
