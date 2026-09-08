package dev.agner.portfolio.integrationTest.helpers

import com.github.tomakehurst.wiremock.http.RequestMethod
import dev.agner.portfolio.integrationTest.config.HttpMockService.ResponseConfiguration
import dev.agner.portfolio.usecase.commons.brazilianLocalDateFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.format

fun ResponseConfiguration.bacenCDIValues(from: String, to: String, values: List<Map<String, String>>) {
    method = RequestMethod.GET
    endpoint = "/dados/serie/bcdata.sgs.11/dados"
    queryParams = mapOf(
        "dataInicial" to LocalDate.parse(from).format(brazilianLocalDateFormat),
        "dataFinal" to LocalDate.parse(to).format(brazilianLocalDateFormat),
    )
    payload = values
}

fun ResponseConfiguration.oneTimeTask() {
    method = RequestMethod.POST
    endpoint = "/tasks/one-time"
}
