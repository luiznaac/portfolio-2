package dev.agner.portfolio.httpapi.controller

import io.ktor.server.routing.Routing

typealias RouteDefinition = Routing.() -> Unit

interface ControllerTemplate {

    fun routes(): RouteDefinition
}
