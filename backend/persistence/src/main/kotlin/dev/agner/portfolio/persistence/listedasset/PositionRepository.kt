package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.select
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

@Component
class PositionRepository : IPositionRepository {

    override suspend fun fetchAll(): List<Position> = transaction {
        PositionTable.select(PositionTable.columns)
            .orderBy(PositionTable.ticker to SortOrder.ASC)
            .map { it.toPosition() }
    }

    override suspend fun fetchBy(ticker: String): Position? = transaction {
        PositionTable.select(PositionTable.columns)
            .where { PositionTable.ticker eq ticker }
            .singleOrNull()
            ?.toPosition()
    }

    // Upsert keyed on the ticker's primary key.
    override suspend fun save(position: Position) {
        transaction {
            PositionTable.batchUpsert(listOf(position)) { p ->
                this[PositionTable.ticker] = p.ticker
                this[PositionTable.quantity] = p.quantity
                this[PositionTable.averagePrice] = p.averagePrice
            }
        }
    }
}
