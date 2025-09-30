package dev.agner.portfolio.usecase.index

import dev.agner.portfolio.usecase.extension.logger
import dev.agner.portfolio.usecase.extension.nextDay
import dev.agner.portfolio.usecase.extension.yesterday
import dev.agner.portfolio.usecase.index.gateway.IIndexValueGateway
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.repository.IIndexValueRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.asFlow
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.toList
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.plus
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class IndexValueService(
    private val repository: IIndexValueRepository,
    private val gateway: IIndexValueGateway,
    private val clock: Clock,
) {
    suspend fun fetchAllBy(indexId: IndexId) = repository.fetchAllBy(indexId)

    suspend fun fetchAllBy(indexId: IndexId, from: LocalDate) = repository.fetchAllBy(indexId, from)

    suspend fun hydrateIndexValues(indexId: IndexId) =
        buildDates(indexId)
            .onEach { log.info("Fetching values for {} from {} to {}", indexId, it.first, it.second) }
            .map { gateway.getIndexValuesForDateRange(indexId, it.first, it.second) }
            .map { it.map { idx -> idx.toCreation() } }
            .onEach { repository.saveAll(indexId, it) }
            .map { it.count() }
            .toList()
            .sum()

    private suspend fun buildDates(indexId: IndexId): Flow<Pair<LocalDate, LocalDate>> {
        val startDate = resolveStartDate(indexId)
        val endDate = LocalDate.yesterday(clock)

        if (startDate > endDate) return emptyFlow()

        return generateSequence(startDate) { prev ->
            val next = prev.plus(100, DateTimeUnit.DAY).plus(1, DateTimeUnit.DAY)
            if (next > endDate) null else next
        }
            .map { currentStart ->
                val currentEnd = minOf(currentStart.plus(100, DateTimeUnit.DAY), endDate)
                currentStart to currentEnd
            }
            .asFlow()
    }

    private suspend fun resolveStartDate(indexId: IndexId) =
        repository.fetchLastBy(indexId)?.date?.nextDay() ?: "2025-01-01".run(LocalDate::parse)

    private val log = logger()
}
