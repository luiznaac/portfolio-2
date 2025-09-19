package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.health.GetAppHealthStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.withContext
import org.springframework.stereotype.Component
import java.time.Instant

@Component
class HealthController(
    private val healthHandler: HealthHandler,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/health") {
            get {
                call.respond(HttpStatusCode.OK, mapOf("is_healthy" to true))
            }

            post {
                val pingPongMessage = call.receive<HealthRequest>()
                val healthStatuses = healthHandler.healthCheck(pingPongMessage.pingPong)

                call.respond(HttpStatusCode.OK, healthStatuses)
            }
        }
    }
}

@Component
class HealthHandler(
    private val appHealth: GetAppHealthStatus,
) {

    suspend fun healthCheck(message: String) = withContext(Dispatchers.IO) {
        setOf(
            async { HealthResponse("ping-pong", message, true, Instant.now()) },
            async { HealthResponse("app", "calls itself", appHealth.getHealthStatus().isHealthy, Instant.now()) },
        ).awaitAll()
    }
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
