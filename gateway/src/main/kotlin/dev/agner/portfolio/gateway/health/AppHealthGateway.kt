package dev.agner.portfolio.gateway.health

import dev.agner.portfolio.usecase.health.HealthGateway
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.request.get
import org.springframework.stereotype.Component

@Component
class AppHealthGateway(
    private val client: HttpClient,
) : HealthGateway {

    override suspend fun isHealthy() = client
        .get("http://localhost:8080/health/internal")
        .body<HealthData>()
        .isHealthy
}

private data class HealthData(val isHealthy: Boolean)
