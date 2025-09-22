package dev.agner.portfolio.usecase.index

import dev.agner.portfolio.usecase.extension.logger
import dev.agner.portfolio.usecase.extension.now
import dev.agner.portfolio.usecase.index.gateway.IIndexValueGateway
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.repository.IIndexValueRepository
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.springframework.stereotype.Service

@Service
class IndexValueService(
    private val repository: IIndexValueRepository,
    private val gateway: IIndexValueGateway,
) {
    suspend fun fetchAllIndexValuesBy(indexId: IndexId) = repository.fetchAllIndexValuesBy(indexId)

    suspend fun hydrateIndexValues(indexId: IndexId) =
        buildDates()
            .asFlow()
            .onEach { log.info("Fetching values for {} from {} to {}", indexId, it.first, it.second) }
            .map { gateway.getIndexValuesForDateRange(indexId, it.first, it.second) }
            .map { it.map { idx -> idx.toIndexValue() } }
            .onEach { repository.persistIndexValues(indexId, it) }

    private fun buildDates(): List<Pair<LocalDate, LocalDate>> {
        var startDate = "2018-01-01".run(LocalDate::parse) // TODO(): define start date by checking last stored date
        val endDate = LocalDate.now()
        val periods = mutableListOf<Pair<LocalDate, LocalDate>>()

        while (startDate <= endDate) {
            val currentEnd = minOf(startDate.plus(50, DateTimeUnit.DAY), endDate)
            periods.add(startDate to currentEnd)
            startDate = currentEnd.plus(1, DateTimeUnit.DAY)
        }

        return periods
    }

    private val log = logger()
}
