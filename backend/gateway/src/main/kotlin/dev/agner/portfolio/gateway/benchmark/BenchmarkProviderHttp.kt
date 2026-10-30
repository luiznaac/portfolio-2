package dev.agner.portfolio.gateway.benchmark

import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkObservation
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkSource
import io.ktor.client.call.body
import io.ktor.client.statement.HttpResponse
import io.ktor.http.HttpStatusCode
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime
import org.slf4j.Logger
import kotlin.time.ExperimentalTime
import kotlin.time.Instant

// A provider window plus the identity used in its failure log, so the two HTTP chain adapters share
// the same "an unavailable window is empty, not an exception" rule.
internal data class ProviderWindow(
    val source: BenchmarkSource,
    val index: BenchmarkIndex,
    val from: LocalDate,
    val to: LocalDate,
)

internal suspend inline fun <reified T> HttpResponse.benchmarkObservationsOrEmpty(
    window: ProviderWindow,
    log: Logger,
    parse: (T) -> List<BenchmarkObservation>,
): List<BenchmarkObservation> = when (status) {
    HttpStatusCode.OK -> parse(body())
    else -> {
        log.warn(
            "{} unavailable for {} between {} and {}: {}",
            window.source,
            window.index,
            window.from,
            window.to,
            status,
        )
        emptyList()
    }
}

@OptIn(ExperimentalTime::class)
internal fun epochSecondsToUtcDate(seconds: Long): LocalDate =
    Instant.fromEpochSeconds(seconds).toLocalDateTime(TimeZone.UTC).date
