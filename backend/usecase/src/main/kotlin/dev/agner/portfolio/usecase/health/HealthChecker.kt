package dev.agner.portfolio.usecase.health

interface HealthChecker {

    suspend fun getHealthStatus(): HealthCheckResult
}
