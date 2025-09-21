package dev.agner.portfolio.gateway.index

import dev.agner.portfolio.usecase.index.gateway.IIndexGateway
import dev.agner.portfolio.usecase.index.model.IndexId
import dev.agner.portfolio.usecase.index.model.TheirIndexValue
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.http.path
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format
import kotlinx.datetime.format.FormatStringsInDatetimeFormats
import kotlinx.datetime.format.byUnicodePattern
import org.springframework.stereotype.Service

@Service
class BacenGateway(
    private val client: HttpClient,
) : IIndexGateway {

    override suspend fun getIndexValuesForDateRange(indexId: IndexId, from: LocalDate, to: LocalDate) =
        client.get("https://api.bcb.gov.br") {
            url {
                path("/dados/serie/bcdata.sgs.${indexId.getBacenCode()}/dados")
                parameter("formato", "json")
                parameter("dataInicial", from.format(format))
                parameter("dataFinal", to.format(format))
            }
        }
            .body<List<BacenIndexValue>>()
            .map { it.toDomain() }
}

private fun IndexId.getBacenCode() = when (this) {
    IndexId.CDI -> 11
    IndexId.IPCA -> 433
    IndexId.SELIC -> 12
}

@OptIn(FormatStringsInDatetimeFormats::class)
private val format = LocalDate.Format { byUnicodePattern("dd/MM/yyyy") }

private data class BacenIndexValue(val data: String, val valor: Double) {
    fun toDomain() = TheirIndexValue(
        date = format.parse(data),
        value = valor,
    )
}
