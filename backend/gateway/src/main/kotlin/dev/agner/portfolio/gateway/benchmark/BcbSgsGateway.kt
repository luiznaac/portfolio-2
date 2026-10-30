package dev.agner.portfolio.gateway.benchmark

import dev.agner.portfolio.usecase.commons.brazilianLocalDateFormat
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkIndex
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkObservation
import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkSource
import dev.agner.portfolio.usecase.performance.gateway.IBenchmarkSeriesSource
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.math.BigDecimal

// BCB SGS: the official, free, key-less source for the rate indexes. `/dados/serie/bcdata.sgs.<code>/dados`
// returns `[{data, valor}]` for the requested interval. The index's own code (CDI 12, IPCA 433) comes
// from the domain enum so the URL is the only place the number appears. IBOV is not an SGS series.
@Service
class BcbSgsGateway(
    private val client: HttpClient,
    @param:Value("\${gateways.sgs.host}") private val host: String,
) : IBenchmarkSeriesSource {

    override val source = BenchmarkSource.SGS
    override val order = 10
    override val indexes = setOf(BenchmarkIndex.CDI, BenchmarkIndex.IPCA)

    override suspend fun fetchSeries(
        index: BenchmarkIndex,
        from: LocalDate,
        to: LocalDate,
    ): List<BenchmarkObservation> = client.get(host) {
        url {
            path("/dados/serie/bcdata.sgs.${index.sgsCode}/dados")
            parameter("formato", "json")
            parameter("dataInicial", from.format(brazilianLocalDateFormat))
            parameter("dataFinal", to.format(brazilianLocalDateFormat))
        }
    }
        .run {
            when (status) {
                HttpStatusCode.OK -> body<List<SgsObservation>>().map { it.toDomain() }
                HttpStatusCode.NotFound -> emptyList()
                else -> error("Error fetching ${index.name} benchmark series from BCB SGS: $status")
            }
        }
}

private data class SgsObservation(val data: String, val valor: BigDecimal) {
    fun toDomain() = BenchmarkObservation(
        date = brazilianLocalDateFormat.parse(data),
        value = valor,
    )
}
