package dev.agner.portfolio.persistence.trade

import dev.agner.portfolio.persistence.listedasset.ListedAssetEntity
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.trade.model.TradeCreation
import dev.agner.portfolio.usecase.trade.model.TradeSide
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import kotlinx.datetime.LocalDate
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.greaterEq
import org.jetbrains.exposed.v1.core.lessEq
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

    override suspend fun fetchByDateRange(start: LocalDate, end: LocalDate) = transaction {
        TradeEntity.find { (TradeTable.date greaterEq start) and (TradeTable.date lessEq end) }
            .orderBy(TradeTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun save(assetId: Int, creation: TradeCreation) = transaction {
        TradeEntity.new {
            listedAsset = ListedAssetEntity.findById(assetId)
                ?: throw IllegalArgumentException("Listed asset with ID $assetId not found")
            date = creation.date
            quantity = when (creation.side) {
                TradeSide.BUY -> creation.quantity
                TradeSide.SELL -> creation.quantity.negate()
            }
            price = creation.price
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }
}
