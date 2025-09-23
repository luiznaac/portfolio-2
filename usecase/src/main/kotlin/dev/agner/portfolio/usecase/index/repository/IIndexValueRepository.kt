package dev.agner.portfolio.usecase.index.repository

import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.IndexValue

interface IIndexValueRepository {

    suspend fun fetchAllIndexValuesBy(indexId: IndexId): Set<IndexValue>

    suspend fun saveAll(indexId: IndexId, indexValues: List<IndexValue>)
}
