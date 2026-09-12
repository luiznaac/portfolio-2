package dev.agner.portfolio.usecase.commons

abstract class DomainException(
    val error: String,
    val userMessage: String,
    val detail: String,
) : RuntimeException(detail)
