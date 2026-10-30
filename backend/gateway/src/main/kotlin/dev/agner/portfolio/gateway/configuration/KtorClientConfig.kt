package dev.agner.portfolio.gateway.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import io.ktor.client.HttpClient
import io.ktor.client.plugins.HttpTimeout
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration

@Configuration
class KtorClientConfig(
    private val mapper: ObjectMapper,
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

        install(ContentNegotiation) {
            register(ContentType.Application.Json, JacksonConverter(mapper))
        }

//        install(Logging)
    }
}
