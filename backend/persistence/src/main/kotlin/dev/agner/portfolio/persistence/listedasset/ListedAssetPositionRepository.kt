package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.listedasset.position.model.ListedAssetPosition
import dev.agner.portfolio.usecase.listedasset.position.repository.IListedAssetPositionRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class ListedAssetPositionRepository(
    private val clock: Clock,
) : IListedAssetPositionRepository {

    override suspend fun fetchByAssetId(assetId: Int) = transaction {
        ListedAssetPositionEntity.find { ListedAssetPositionTable.listedAsset eq assetId }
            .orderBy(ListedAssetPositionTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun save(assetId: Int, position: ListedAssetPosition): Unit = transaction {
        val existing = ListedAssetPositionEntity.find {
            (ListedAssetPositionTable.listedAsset eq assetId) and (ListedAssetPositionTable.date eq position.date)
        }.firstOrNull()

        if (existing != null) {
            existing.principal = position.principal
            existing.yieldValue = position.yield
            existing.taxes = position.taxes
        } else {
            val asset = ListedAssetEntity.findById(assetId)
                ?: throw IllegalArgumentException("Listed asset with ID $assetId not found")

            ListedAssetPositionEntity.new {
                listedAsset = asset
                date = position.date
                principal = position.principal
                yieldValue = position.yield
                taxes = position.taxes
                createdAt = LocalDateTime.now(clock)
            }
        }
    }
}

private fun ListedAssetPositionEntity.toModel() = ListedAssetPosition(
    date = date,
    principal = principal,
    yield = yieldValue,
    taxes = taxes,
)
