package dev.agner.portfolio.gateway.benchmark

import com.fasterxml.jackson.annotation.JsonProperty
import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkObservation
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkSource
import dev.agner.portfolio.usecase.performance.gateway.IBenchmarkSeriesSource
import io.ktor.client.HttpClient
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.appendPathSegments
import kotlinx.datetime.LocalDate
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal

// brapi.dev's quote history for the Bovespa index. Its free plan clamps any requested window to the
// last three months, so a key-less call asks for `3mo` and the store falls through to Yahoo for the
// rest; a paid token asks for `max` and can carry more of the history itself.
@Service
class BrapiGateway(
    private val client: HttpClient,
    @param:Value("\${gateways.brapi.host}") private val host: String,
    @param:Value("\${gateways.brapi.token:}") private val token: String,
) : IBenchmarkSeriesSource {

    override val source = BenchmarkSource.BRAPI
    override val order = 10
    override val indexes = setOf(BenchmarkIndex.IBOV)

    private val log = logger()

    override suspend fun fetchSeries(
        index: BenchmarkIndex,
        from: LocalDate,
        to: LocalDate,
    ): List<BenchmarkObservation> = client.get(host) {
        url {
            appendPathSegments("api", "quote", "^BVSP")
            parameter("range", if (token.isNotBlank()) "max" else "3mo")
            parameter("interval", "1d")
            if (token.isNotBlank()) parameter("token", token)
        }
    }.benchmarkObservationsOrEmpty<BrapiResponse>(
        ProviderWindow(source, index, from, to),
        log,
    ) { it.toObservations() }
}

private data class BrapiResponse(val results: List<BrapiResult>? = null)

private data class BrapiResult(
    @param:JsonProperty("historicalDataPrice") val historicalDataPrice: List<BrapiPoint>? = null,
)

private data class BrapiPoint(val date: Long, val close: BigDecimal? = null)

private fun BrapiResponse.toObservations() = results.orEmpty()
    .flatMap { it.historicalDataPrice.orEmpty() }
    .mapNotNull { point ->
        point.close?.let { BenchmarkObservation(date = epochSecondsToUtcDate(point.date), value = it) }
    }
