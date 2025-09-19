package dev.agner.portfolio.httpapi.controller

import io.ktor.server.response.respondText
import io.ktor.server.routing.Routing
import io.ktor.server.routing.get
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.stereotype.Component
import java.time.Instant

@Configuration
class HealthCheck {

    @Bean
    fun healthCheckRoute(healthHandler: HealthHandler): Routing.() -> Unit = {
        get("/health") {
            val healthStatuses = healthHandler.healthCheck()

            call.respondText(
                text = " bom dia amigos "
            )
        }
    }
}

@Component
class HealthHandler {

    suspend fun healthCheck() = HealthResponse(true, Instant.now())
}

data class HealthResponse(
    val isHealthy: Boolean,
    val timestamp: Instant,
)
