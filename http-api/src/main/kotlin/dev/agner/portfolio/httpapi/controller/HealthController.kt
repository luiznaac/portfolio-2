package dev.agner.portfolio.httpapi.controller

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.server.ServerRequest
import org.springframework.web.reactive.function.server.ServerResponse
import org.springframework.web.reactive.function.server.bodyValueAndAwait
import org.springframework.web.reactive.function.server.coRouter
import java.time.Instant

@Configuration
class HealthController {

    @Bean
    fun healthRoute(healthUseCase: HealthUseCase) = coRouter {
        GET("/health", healthUseCase::healthCheck)
    }
}

@Component
class HealthUseCase {

    suspend fun healthCheck(request: ServerRequest): ServerResponse {
        return ServerResponse.ok().bodyValueAndAwait(HealthResponse(true, Instant.now()))
    }
}

private data class HealthResponse(
    val isHealthy: Boolean,
    val timestamp: Instant,
)
