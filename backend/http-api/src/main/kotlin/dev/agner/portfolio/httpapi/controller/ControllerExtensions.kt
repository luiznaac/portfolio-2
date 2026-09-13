package dev.agner.portfolio.httpapi.controller

import io.ktor.server.application.ApplicationCall

internal fun ApplicationCall.requiredInt(name: String): Int =
    parameters[name]?.toIntOrNull() ?: throw InvalidParameterException(name, parameters[name])

internal fun ApplicationCall.requiredString(name: String): String =
    parameters[name] ?: throw InvalidParameterException(name, null)

internal fun ApplicationCall.strategyId(): Int = requiredInt("strategy_id")
