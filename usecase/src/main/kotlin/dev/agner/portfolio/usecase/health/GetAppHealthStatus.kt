package dev.agner.portfolio.usecase.health

import org.springframework.stereotype.Service

@Service
class GetAppHealthStatus(
    private val healthGateway: HealthGateway,
) {

    suspend fun getHealthStatus() = healthGateway.getHealthStatus()
}
