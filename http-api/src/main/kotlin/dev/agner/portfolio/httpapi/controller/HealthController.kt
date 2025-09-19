package dev.agner.portfolio.httpapi.controller

import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class HealthController(
    private val healthHandler: HealthHandler,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        post ("/health") {
            val pingPongMessage = call.receive<HealthRequest>()
            val healthStatuses = healthHandler.healthCheck(pingPongMessage.pingPong)

            call.respond(HttpStatusCode.OK, healthStatuses)
        }
    }
}

@Component
class HealthHandler {

    suspend fun healthCheck(message: String) = setOf(
        HealthResponse(serviceName = "ping-pong", description = message, isHealthy = true, timestamp = Instant.now()),
    )
}

data class HealthResponse(
    val serviceName: String,
    val description: String? = null,
    val isHealthy: Boolean,
    val timestamp: Instant,
)

data class HealthRequest(
    val pingPong: String,
)
