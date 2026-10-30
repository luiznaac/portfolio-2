package dev.agner.portfolio.gateway

import dev.agner.portfolio.usecase.configuration.JsonMapper
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.MockRequestHandler
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter

// A Ktor client wired the way KtorClientConfig wires the real one, over a MockEngine — the shared
// fixture for the gateway contract tests.
internal fun clientWith(handler: MockRequestHandler) = HttpClient(MockEngine(handler)) {
    install(HttpTimeout) {
        requestTimeoutMillis = 60_000
        connectTimeoutMillis = 30_000
        socketTimeoutMillis = 60_000
    }

    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
    }
}
