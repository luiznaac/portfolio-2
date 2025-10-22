package dev.agner.portfolio.usecase.health

import kotlinx.datetime.LocalDateTime

data class HealthCheckResult(
    val serviceName: String,
    val isHealthy: Boolean,
    val timestamp: LocalDateTime,
)
