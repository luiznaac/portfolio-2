package dev.agner.portfolio.usecase.health

interface HealthGateway {
    suspend fun isHealthy(): Boolean
}
