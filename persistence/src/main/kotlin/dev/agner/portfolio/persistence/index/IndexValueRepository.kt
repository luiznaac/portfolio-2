package dev.agner.portfolio.persistence.index

import dev.agner.portfolio.usecase.extension.mapToSet
import dev.agner.portfolio.usecase.extension.now
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.IndexValue
import dev.agner.portfolio.usecase.index.repository.IIndexValueRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Service
import kotlin.time.ExperimentalTime

@Service
class IndexValueRepository : IIndexValueRepository {

    override suspend fun fetchAllIndexValuesBy(indexId: IndexId): Set<IndexValue> = transaction {
        IndexValueEntity.find { IndexValueTable.indexId eq indexId.name }.mapToSet { it.toIndexValue() }
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun saveAll(indexId: IndexId, indexValues: List<IndexValue>): Unit = transaction {
        IndexValueTable.batchInsert(indexValues) { (date, value) ->
            this[IndexValueTable.indexId] = indexId.name
            this[IndexValueTable.date] = date
            this[IndexValueTable.value] = value.toBigDecimal()
            this[IndexValueTable.createdAt] = LocalDateTime.now()
        }
    }
}
