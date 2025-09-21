package dev.agner.portfolio.usecase.index.model

import java.time.Instant

data class Index(
    val id: String,
    val createdAt: Instant,
)
