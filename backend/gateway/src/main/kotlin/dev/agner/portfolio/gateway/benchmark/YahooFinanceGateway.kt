package dev.agner.portfolio.gateway.benchmark

import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkObservation
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkSource
import dev.agner.portfolio.usecase.performance.gateway.IBenchmarkSeriesSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.header
import io.ktor.client.request.parameter
import io.ktor.http.HttpHeaders
import io.ktor.http.appendPathSegments
import kotlinx.datetime.DateTimeUnit
import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.atStartOfDayIn
import kotlinx.datetime.plus
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal
import kotlin.time.ExperimentalTime

// Yahoo Finance's chart API for `^BVSP`, the fallback behind brapi. It serves the whole history, so
// it carries the years brapi's free plan cannot reach. A failed chart is an empty list, not an
// exception, so the store keeps whatever it already has.
@Service
class YahooFinanceGateway(
    private val client: HttpClient,
    @param:Value("\${gateways.yahoo.host}") private val host: String,
) : IBenchmarkSeriesSource {

    override val source = BenchmarkSource.YAHOO
    override val order = 20
    override val indexes = setOf(BenchmarkIndex.IBOV)

    private val log = logger()

    override suspend fun fetchSeries(
        index: BenchmarkIndex,
        from: LocalDate,
        to: LocalDate,
    ): List<BenchmarkObservation> = client.get(host) {
        header(HttpHeaders.UserAgent, "Mozilla/5.0 (compatible; portfolio-2)")
        url {
            appendPathSegments("v8", "finance", "chart", "^BVSP")
            parameter("period1", from.startOfDayEpochSeconds())
            parameter("period2", to.exclusiveEndEpochSeconds())
            parameter("interval", "1d")
        }
    }.benchmarkObservationsOrEmpty<YahooChartResponse>(
        ProviderWindow(source, index, from, to),
        log,
    ) { it.toObservations() }
}

private data class YahooChartResponse(val chart: YahooChart? = null)

private data class YahooChart(val result: List<YahooResult>? = null)

private data class YahooResult(
    val timestamp: List<Long>? = null,
    val indicators: YahooIndicators? = null,
)

private data class YahooIndicators(val quote: List<YahooQuote>? = null)

private data class YahooQuote(val close: List<BigDecimal?>? = null)

private fun YahooChartResponse.toObservations(): List<BenchmarkObservation> {
    val result = chart?.result?.firstOrNull() ?: return emptyList()
    val timestamps = result.timestamp ?: return emptyList()
    val closes = result.indicators?.quote?.firstOrNull()?.close ?: return emptyList()

    return timestamps.zip(closes).mapNotNull { (timestamp, close) ->
        close?.let { BenchmarkObservation(date = epochSecondsToUtcDate(timestamp), value = it) }
    }
}

@OptIn(ExperimentalTime::class)
private fun LocalDate.startOfDayEpochSeconds() = atStartOfDayIn(TimeZone.UTC).epochSeconds

// Yahoo treats `period2` as an exclusive bound.
@OptIn(ExperimentalTime::class)
private fun LocalDate.exclusiveEndEpochSeconds() =
    plus(1, DateTimeUnit.DAY).startOfDayEpochSeconds()
