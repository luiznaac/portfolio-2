package dev.agner.portfolio.persistence.attribution

import dev.agner.portfolio.persistence.listedasset.ListedAssetEntity
import dev.agner.portfolio.persistence.strategy.StrategyEntity
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.repository.IAttributionRepository
import dev.agner.portfolio.usecase.commons.now
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class AttributionRepository(
    private val clock: Clock,
) : IAttributionRepository {

    override suspend fun fetchByAssetId(assetId: Int) = transaction {
        AttributionMovementEntity.find { AttributionMovementTable.listedAsset eq assetId }
            .orderBy(AttributionMovementTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun save(creation: AttributionMovementCreation) = transaction {
        AttributionMovementEntity.new {
            listedAsset = ListedAssetEntity.findById(creation.listedAssetId)
                ?: throw IllegalArgumentException("Listed asset with ID ${creation.listedAssetId} not found")
            strategy = StrategyEntity.findById(creation.strategyId)
                ?: throw IllegalArgumentException("Strategy with ID ${creation.strategyId} not found")
            date = creation.date
            quantity = creation.quantity
            reason = creation.reason.name
            note = creation.note
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }
}
