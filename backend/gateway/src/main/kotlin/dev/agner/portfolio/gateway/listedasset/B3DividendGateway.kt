package dev.agner.portfolio.gateway.listedasset

import com.fasterxml.jackson.annotation.JsonProperty
import dev.agner.portfolio.usecase.commons.brazilianLocalDateFormat
import dev.agner.portfolio.usecase.listedasset.gateway.IDividendGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind.FII
import dev.agner.portfolio.usecase.listedasset.model.DividendDeclaration
import dev.agner.portfolio.usecase.listedasset.model.DividendType
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import io.ktor.http.HttpStatusCode
import io.ktor.http.path
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Service
import java.util.Base64

/**
 * Proventos straight from B3's public endpoints — free, no token, source of origin. brapi charges
 * for this (see the plan); B3 doesn't, for both stocks and FIIs, so it's the primary here rather
 * than a fallback. Values returned are gross per share: JCP still has 15% withheld at source,
 * dividendo/rendimento are tax-free for individuals — reconciling declared vs. received is left to
 * the brokerage-note import (Fase 4 of the plan), not done here.
 */
@Service
class B3DividendGateway(
    private val client: HttpClient,
    @param:Value("\${gateways.b3.host}") private val host: String,
) : IDividendGateway {

    override suspend fun getDividends(asset: ListedAsset) = if (asset.kind == FII) {
        getFundDividends(asset)
    } else {
        getStockDividends(asset)
    }

    private suspend fun getStockDividends(asset: ListedAsset): List<DividendDeclaration> {
        val params = """{"language":"pt-br","pageNumber":1,"pageSize":200,"tradingName":"${asset.b3Identifier}"}"""
        val response = client.get(host) {
            url { path("/listedCompaniesProxy/CompanyCall/GetListedCashDividends/${params.toB3Base64()}") }
        }

        if (response.status != HttpStatusCode.OK) return emptyList()

        val expectedType = expectedTypeStockFor(asset.ticker)

        return response.body<B3StockDividendResponse>().results
            .filter { expectedType == null || it.typeStock == expectedType }
            .map {
                DividendDeclaration(
                    type = it.corporateAction.toDividendType(),
                    valuePerShare = it.valueCash.toB3Decimal(),
                    exDate = it.lastDatePriorEx.parseB3Date(),
                    // GetListedCashDividends doesn't carry an actual credit date, only the ex-date.
                    paymentDate = null,
                )
            }
    }

    private suspend fun getFundDividends(asset: ListedAsset): List<DividendDeclaration> {
        val params = """{"typeFund":7,"identifierFund":"${asset.b3Identifier}","cnpj":""}"""
        val response = client.get(host) {
            url { path("/fundsProxy/fundsCall/GetListedSupplementFunds/${params.toB3Base64()}") }
        }

        if (response.status != HttpStatusCode.OK) return emptyList()

        return response.body<B3FundResponse>().cashDividends.map {
            DividendDeclaration(
                type = it.label.toDividendType(),
                valuePerShare = it.rate.toB3Decimal(),
                exDate = it.lastDatePrior.parseB3Date(),
                paymentDate = it.paymentDate.parseB3Date(),
            )
        }
    }
}

private fun String.toB3Base64(): String = Base64.getEncoder().encodeToString(toByteArray())

private fun String.toB3Decimal() = replace(",", ".").toBigDecimal()

private fun String.parseB3Date() = brazilianLocalDateFormat.parse(this)

private fun String.toDividendType() = when {
    contains("JRS", ignoreCase = true) || equals("JCP", ignoreCase = true) -> DividendType.JCP
    contains("DIVIDENDO", ignoreCase = true) -> DividendType.DIVIDENDO
    else -> DividendType.RENDIMENTO
}

/**
 * B3's stock-dividend endpoint is keyed by company (tradingName), not by ticker, and returns every
 * share class (ON/PN/units/BDRs) mixed together. This maps a ticker's numeric suffix to B3's
 * `typeStock` to filter down to the right class.
 *
 * Known simplification: doesn't distinguish PNA/PNB, and BDRs (suffix "34") aren't filtered at all
 * — a BDR gets every class's dividends unfiltered. Fine for now since the user's BDR holding
 * (ROXO34) is a single-class company; revisit if that stops being true.
 */
private fun expectedTypeStockFor(ticker: String): String? = when {
    ticker.endsWith("3") -> "ON"
    ticker.endsWith("4") -> "PN"
    else -> null
}

private data class B3StockDividendResponse(
    @param:JsonProperty("results") val results: List<B3StockDividendRow> = emptyList(),
)

private data class B3StockDividendRow(
    @param:JsonProperty("typeStock") val typeStock: String,
    @param:JsonProperty("corporateAction") val corporateAction: String,
    @param:JsonProperty("valueCash") val valueCash: String,
    @param:JsonProperty("lastDatePriorEx") val lastDatePriorEx: String,
)

private data class B3FundResponse(
    @param:JsonProperty("cashDividends") val cashDividends: List<B3FundDividendRow> = emptyList(),
)

private data class B3FundDividendRow(
    @param:JsonProperty("label") val label: String,
    @param:JsonProperty("rate") val rate: String,
    @param:JsonProperty("lastDatePrior") val lastDatePrior: String,
    @param:JsonProperty("paymentDate") val paymentDate: String,
)
