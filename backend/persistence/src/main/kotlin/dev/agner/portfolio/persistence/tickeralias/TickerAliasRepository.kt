package dev.agner.portfolio.persistence.tickeralias

import dev.agner.portfolio.usecase.tickeralias.model.TickerAlias
import dev.agner.portfolio.usecase.tickeralias.repository.ITickerAliasRepository
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.jdbc.batchUpsert
import org.jetbrains.exposed.v1.jdbc.selectAll
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component

@Component
class TickerAliasRepository : ITickerAliasRepository {

    override suspend fun fetchAll(): List<TickerAlias> = transaction {
        TickerAliasTable.selectAll()
            .orderBy(TickerAliasTable.oldTicker to SortOrder.ASC)
            .map { it.toTickerAlias() }
    }

    // Upsert keyed on the old ticker's primary key: re-importing a TICKER_CHANGE updates its edge.
    override suspend fun save(alias: TickerAlias): TickerAlias = transaction {
        TickerAliasTable.batchUpsert(listOf(alias)) { row ->
            this[TickerAliasTable.oldTicker] = row.oldTicker
            this[TickerAliasTable.canonicalTicker] = row.canonicalTicker
            this[TickerAliasTable.effectiveDate] = row.effectiveDate
            this[TickerAliasTable.sourceType] = row.source
            this[TickerAliasTable.ratio] = row.ratio
            this[TickerAliasTable.corporateActionId] = row.corporateActionId
        }
        alias
    }
}
