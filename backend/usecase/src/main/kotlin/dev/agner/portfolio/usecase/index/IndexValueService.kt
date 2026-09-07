package dev.agner.portfolio.usecase.index

import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.commons.mapAsync
import dev.agner.portfolio.usecase.commons.mapAsyncDeferred
import dev.agner.portfolio.usecase.commons.nextDay
import dev.agner.portfolio.usecase.commons.onEachAsyncDeferred
import dev.agner.portfolio.usecase.commons.yesterday
import dev.agner.portfolio.usecase.index.gateway.IIndexValueGateway
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.repository.IIndexValueRepository
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.map
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
            .mapAsync { gateway.getIndexValuesForDateRange(indexId, it.first, it.second) }
            .mapAsyncDeferred { it.map { idx -> idx.toCreation() } }
            .onEachAsyncDeferred { repository.saveAll(indexId, it) }
            .awaitAll()
            .sumOf { it.count() }

    private suspend fun buildDates(indexId: IndexId): List<Pair<LocalDate, LocalDate>> {
        val startDate = resolveStartDate(indexId)
        val endDate = LocalDate.yesterday(clock)

        if (startDate > endDate) return emptyList()

        return generateSequence(startDate) { prev ->
            val next = prev.plus(100, DateTimeUnit.DAY).plus(1, DateTimeUnit.DAY)
            if (next > endDate) null else next
        }
            .map { currentStart ->
                val currentEnd = minOf(currentStart.plus(100, DateTimeUnit.DAY), endDate)
                currentStart to currentEnd
            }
            .toList()
    }

    private suspend fun resolveStartDate(indexId: IndexId) =
        repository.fetchLastBy(indexId)?.date?.nextDay() ?: "2020-01-01".run(LocalDate::parse)

    private val log = logger()
}
