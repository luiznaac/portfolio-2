package dev.agner.portfolio.gateway.health

import dev.agner.portfolio.usecase.health.HealthCheckResult
import dev.agner.portfolio.usecase.health.HealthChecker
import dev.agner.portfolio.usecase.health.HealthGateway
import org.springframework.stereotype.Service

@Service
class HttpClientHealthCheck(
    private val healthGateway: HealthGateway,
) : HealthChecker {

    override suspend fun getHealthStatus() = HealthCheckResult(
        serviceName = "http-client",
        isHealthy = healthGateway.isHealthy(),
    )
}
