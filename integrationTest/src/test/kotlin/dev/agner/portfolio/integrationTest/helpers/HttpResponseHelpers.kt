package dev.agner.portfolio.integrationTest.helpers

import com.github.tomakehurst.wiremock.http.RequestMethod
import dev.agner.portfolio.integrationTest.config.HttpMockService.ResponseConfiguration

fun ResponseConfiguration.bacenCDIValues(values: List<Map<String, String>>) {
    method = RequestMethod.GET
    endpoint = "/dados/serie/bcdata.sgs.11/dados"
    payload = values
}
