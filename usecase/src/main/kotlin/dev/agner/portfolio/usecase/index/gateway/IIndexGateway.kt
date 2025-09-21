package dev.agner.portfolio.usecase.index.gateway

import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.TheirIndexValue
import kotlinx.datetime.LocalDate

interface IIndexGateway {

    suspend fun getIndexValuesForDateRange(indexId: IndexId, from: LocalDate, to: LocalDate): List<TheirIndexValue>
}
