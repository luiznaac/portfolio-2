package dev.agner.portfolio.gateway.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.commons.TelemetryAttributes
import dev.agner.portfolio.usecase.commons.requestContext
import io.ktor.client.HttpClient
import io.ktor.client.HttpClientConfig
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.api.createClientPlugin
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import io.opentelemetry.api.OpenTelemetry
import io.opentelemetry.instrumentation.ktor.v3_0.KtorClientTelemetry
import kotlinx.coroutines.currentCoroutineContext
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KtorClientConfig(
    private val mapper: ObjectMapper,
    private val openTelemetry: OpenTelemetry,
) {

    @Bean
    fun ktorClient() = HttpClient {
        // The engine's default request timeout (15s) also cancels a response body still being read,
        // which kills a large COTAHIST annual ZIP before it finishes. These are the floor; a
        // gateway can ask for longer per request (CotahistGateway does for the annual file).
        install(HttpTimeout) {
            requestTimeoutMillis = 60_000
            connectTimeoutMillis = 30_000
            socketTimeoutMillis = 60_000
        }

        configureTelemetry(mapper, openTelemetry)
    }
}

/**
 * The shared outbound client configuration: content negotiation, the semconv client metrics and
 * the `X-Request-ID` propagation every gateway call shares. `docs/observability.md` §3.
 */
internal fun HttpClientConfig<*>.configureTelemetry(mapper: ObjectMapper, openTelemetry: OpenTelemetry) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(mapper))
    }
    install(KtorClientTelemetry) {
        setOpenTelemetry(openTelemetry)
    }
    install(RequestIdPropagation)
}

private val RequestIdPropagation = createClientPlugin("RequestIdPropagation") {
    onRequest { request, _ ->
        currentCoroutineContext().requestContext?.requestId?.let { requestId ->
            request.headers[TelemetryAttributes.REQUEST_ID_HEADER] = requestId
        }
    }
}
