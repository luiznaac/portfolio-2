package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.listedasset.model.ListedAssetCreation
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Service

@Service
class ListedAssetService(
    private val repository: IListedAssetRepository,
) {

    suspend fun create(creation: ListedAssetCreation) = repository.save(creation)

    suspend fun fetchAll() = repository.fetchAll()

    suspend fun fetchById(id: Int) = repository.fetchById(id)
        ?: throw IllegalArgumentException("Listed asset with ID $id not found")

    suspend fun changeTicker(assetId: Int, newTicker: String, effectiveFrom: LocalDate) =
        repository.changeTicker(assetId, newTicker, effectiveFrom)

    suspend fun resolveIdByTicker(ticker: String, date: LocalDate) = repository.resolveIdByTicker(ticker, date)
}
