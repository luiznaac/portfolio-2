package dev.agner.portfolio.usecase.index.repository

import dev.agner.portfolio.usecase.index.model.Index
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.IndexValue

interface IIndexRepository {

    suspend fun fetchAllIndexes(): Set<Index>

    suspend fun fetchAllIndexValuesBy(indexId: IndexId): Set<IndexValue>

    suspend fun persistIndexValues(indexId: IndexId, indexValues: List<IndexValue>)
}
