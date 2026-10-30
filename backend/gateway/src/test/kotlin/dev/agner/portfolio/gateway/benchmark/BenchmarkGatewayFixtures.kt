package dev.agner.portfolio.gateway.benchmark

import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.headersOf

internal fun jsonHeaders() = headersOf(HttpHeaders.ContentType, ContentType.Application.Json.toString())
