package dev.agner.portfolio.gateway.index

import dev.agner.portfolio.usecase.commons.brazilianLocalDateFormat
import dev.agner.portfolio.usecase.index.gateway.IIndexValueGateway
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.TheirIndexValue
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

// BACEN's SGS ("sistema gerenciador de séries temporais") series codes, per the Central Bank's
// series catalogue: CDI is 11, SELIC is 12, IPCA is 433.
private const val CDI_SERIES = 11
private const val SELIC_SERIES = 12
private const val IPCA_SERIES = 433

@Service
class BacenGateway(
    private val client: HttpClient,
    @param:Value("\${gateways.bacen.host}") private val host: String,
) : IIndexValueGateway {

    override suspend fun getIndexValuesForDateRange(indexId: IndexId, from: LocalDate, to: LocalDate) =
        client.get(host) {
            url {
                path("/dados/serie/bcdata.sgs.${indexId.getBacenCode()}/dados")
                parameter("formato", "json")
                parameter("dataInicial", from.format(brazilianLocalDateFormat))
                parameter("dataFinal", to.format(brazilianLocalDateFormat))
            }
        }
            .run {
                when (status) {
                    HttpStatusCode.OK -> body<List<BacenIndexValue>>().map { it.toDomain() }
                    HttpStatusCode.NotFound -> emptyList()
                    else -> error("Error fetching ${indexId.name} values from Bacen: $status")
                }
            }
}

private fun IndexId.getBacenCode() = when (this) {
    IndexId.CDI -> CDI_SERIES
    IndexId.IPCA -> IPCA_SERIES
    IndexId.SELIC -> SELIC_SERIES
}

private data class BacenIndexValue(val data: String, val valor: BigDecimal) {
    fun toDomain() = TheirIndexValue(
        date = brazilianLocalDateFormat.parse(data),
        value = valor,
    )
}
