package dev.agner.portfolio.gateway.health

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
        isHealthy = healthGateway.isHealthy(),
        timestamp = LocalDateTime.now(clock),
    )
}
