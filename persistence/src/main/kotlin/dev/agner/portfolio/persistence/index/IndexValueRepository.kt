package dev.agner.portfolio.persistence.index

import dev.agner.portfolio.usecase.extension.now
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.IndexValue
import dev.agner.portfolio.usecase.index.model.IndexValueCreation
import dev.agner.portfolio.usecase.index.repository.IIndexValueRepository
import kotlinx.datetime.LocalDateTime
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.jdbc.batchInsert
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Service
import java.time.Clock
import kotlin.time.ExperimentalTime

@Service
class IndexValueRepository(
    private val clock: Clock,
) : IIndexValueRepository {

    override suspend fun fetchAllBy(indexId: IndexId): List<IndexValue> = transaction {
        IndexValueEntity.find { IndexValueTable.indexId eq indexId.name }.map { it.toIndexValue() }
    }

    override suspend fun fetchLastBy(indexId: IndexId) = transaction {
        IndexValueEntity.find { IndexValueTable.indexId eq indexId.name }
            .orderBy(IndexValueTable.date to SortOrder.DESC)
            .limit(1)
            .firstOrNull()
            ?.toIndexValue()
    }

    @OptIn(ExperimentalTime::class)
    override suspend fun saveAll(indexId: IndexId, indexValues: List<IndexValueCreation>): Unit = transaction {
        IndexValueTable.batchInsert(indexValues) { (date, value) ->
            this[IndexValueTable.indexId] = indexId.name
            this[IndexValueTable.date] = date
            this[IndexValueTable.value] = value.toBigDecimal()
            this[IndexValueTable.createdAt] = LocalDateTime.now(clock)
        }
    }
}
