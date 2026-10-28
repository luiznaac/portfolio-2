package dev.agner.portfolio.integrationTest.config

import com.github.tomakehurst.wiremock.WireMockServer
import com.github.tomakehurst.wiremock.client.WireMock
import com.github.tomakehurst.wiremock.core.WireMockConfiguration.options
import com.github.tomakehurst.wiremock.http.RequestMethod
import dev.agner.portfolio.usecase.configuration.JsonMapper

object HttpMockService {

    // A dynamic port keeps the suite clear of whatever else the machine runs on a fixed one. The app
    // under test reaches the mocks through this port; DockerComposeExtension publishes it before the
    // Spring context loads.
    private val server = WireMockServer(options().dynamicPort())
    private val mapper = JsonMapper.mapper

    val port: Int get() = server.port()

    init {
        server.start()
    }

    class ResponseConfiguration {
        lateinit var method: RequestMethod
        lateinit var endpoint: String
        var queryParams: Map<String, String> = emptyMap()
        var httpStatus: Int = 200
        var payload: Any = emptyMap<String, String>()

        // A binary body (the COTAHIST daily file is a ZIP) bypasses the JSON serialization above.
        var body: ByteArray? = null
    }

    class ResponseScope {
        val responses = mutableSetOf<ResponseConfiguration>()

        suspend fun response(configure: suspend ResponseConfiguration.() -> Unit) {
            responses += ResponseConfiguration().apply { configure() }
        }
    }

    suspend fun configureResponses(scope: suspend ResponseScope.() -> Unit) {
        ResponseScope()
            .apply { scope() }
            .responses
            .forEach { configuration ->
                mockFor(
                    method = configuration.method,
                    endpoint = configuration.endpoint,
                    queryParams = configuration.queryParams,
                    httpStatus = configuration.httpStatus,
                    desiredResponsePayload = configuration.payload,
                    desiredResponseBody = configuration.body,
                )
            }
    }

    private fun mockFor(
        method: RequestMethod,
        endpoint: String,
        queryParams: Map<String, String>,
        httpStatus: Int,
        desiredResponsePayload: Any,
        desiredResponseBody: ByteArray? = null,
    ) {
        server.stubFor(
            WireMock.request(method.value(), WireMock.urlPathMatching(endpoint))
                .apply {
                    queryParams.forEach { (key, value) -> withQueryParam(key, WireMock.equalTo(value)) }
                }
                .willReturn(
                    WireMock.aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withStatus(httpStatus)
                        .apply {
                            if (desiredResponseBody == null) {
                                withBody(mapper.writeValueAsBytes(desiredResponsePayload))
                            } else {
                                withBody(desiredResponseBody)
                            }
                        },
                ),
        )
    }

    fun clearMocks() {
        server.resetAll()
    }
}
