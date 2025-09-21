package dev.agner.portfolio.persistence.index

import dev.agner.portfolio.usecase.extension.mapToSet
import dev.agner.portfolio.usecase.index.model.Index
import dev.agner.portfolio.usecase.index.repository.IIndexRepository
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Service

@Service
class IndexRepository : IIndexRepository {

    override suspend fun fetchAllIndexes(): Set<Index> = transaction {
        IndexEntity.all().mapToSet { it.toIndex() }
    }
}
