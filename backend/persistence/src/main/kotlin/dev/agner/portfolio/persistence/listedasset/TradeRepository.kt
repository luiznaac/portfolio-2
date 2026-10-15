package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.persistence.institution.InstitutionEntity
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
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

    // The side is folded into the sign of the stored quantity here, and only here.
    override suspend fun save(creation: TradeCreation): Trade = transaction {
        TradeEntity.new {
            ticker = creation.ticker
            date = creation.date
            venue = creation.venue
            quantity = when (creation.side) {
                TradeSide.BUY -> creation.quantity
                TradeSide.SELL -> creation.quantity.negate()
            }
            unitPrice = creation.unitPrice
            institution = InstitutionEntity[creation.institutionId]
            fee = creation.fee
            createdAt = LocalDateTime.now(clock)
        }.toModel()
    }

    override suspend fun fetchAll(): List<Trade> = transaction {
        TradeEntity.all()
            .orderBy(TradeTable.id to SortOrder.ASC)
            .map { it.toModel() }
    }

    override suspend fun fetchAllBy(ticker: String): List<Trade> = transaction {
        TradeEntity.find { TradeTable.ticker eq ticker }
            .orderBy(TradeTable.date to SortOrder.ASC)
            .map { it.toModel() }
    }
}
