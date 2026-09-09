package dev.agner.portfolio.persistence.trade

import dev.agner.portfolio.persistence.listedasset.ListedAssetEntity
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.trade.model.TradeCreation
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class TradeRepository(
    private val clock: Clock,
) : ITradeRepository {

    override suspend fun fetchByAssetId(assetId: Int) = transaction {
        TradeEntity.find { TradeTable.listedAsset eq assetId }
            .orderBy(TradeTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchAll() = transaction {
        TradeEntity.all()
            .orderBy(TradeTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun save(creation: TradeCreation) = transaction {
        TradeEntity.new {
            listedAsset = ListedAssetEntity.findById(creation.assetId)
                ?: throw IllegalArgumentException("Listed asset with ID ${creation.assetId} not found")
            date = creation.date
            quantity = creation.quantity
            price = creation.price
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }
}
