package dev.agner.portfolio.usecase.index.repository

import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.IndexValue
import dev.agner.portfolio.usecase.index.model.IndexValueCreation
import kotlinx.datetime.LocalDate

interface IIndexValueRepository {

    suspend fun fetchAllBy(indexId: IndexId): List<IndexValue>

    suspend fun fetchAllBy(indexId: IndexId, from: LocalDate): List<IndexValue>

    suspend fun fetchLastBy(indexId: IndexId): IndexValue?

    suspend fun saveAll(indexId: IndexId, indexValues: List<IndexValueCreation>)
}
