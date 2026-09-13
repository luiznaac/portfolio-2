package dev.agner.portfolio.gateway.health

import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.health.HealthCheckResult
import dev.agner.portfolio.usecase.health.HealthChecker
import dev.agner.portfolio.usecase.health.HealthGateway
import kotlinx.datetime.LocalDateTime
import org.springframework.stereotype.Service
import java.time.Clock

@Service
class HttpClientHealthCheck(
    private val healthGateway: HealthGateway,
    private val clock: Clock,
) : HealthChecker {

    override suspend fun getHealthStatus() = HealthCheckResult(
        serviceName = "http-client",
        isHealthy = runCatching { healthGateway.isHealthy() }
            .onFailure { logger().warn("HTTP client health check failed", it) }
            .getOrDefault(false),
        timestamp = LocalDateTime.now(clock),
    )
}
