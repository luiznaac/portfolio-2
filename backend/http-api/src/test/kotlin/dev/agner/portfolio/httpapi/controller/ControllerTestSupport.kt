package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.configuration.DefaultDomainExceptionStatusMapper
import dev.agner.portfolio.httpapi.configuration.installDomainExceptionHandler
import dev.agner.portfolio.usecase.configuration.JsonMapper
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.routing.routing

// Installs a controller the way KtorConfig does — JSON via the shared mapper and the real domain
// exception -> ApiError mapping — so a controller test observes the exact edge contract.
internal fun Application.installRoutes(controller: ControllerTemplate) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
    }

    installDomainExceptionHandler(DefaultDomainExceptionStatusMapper())

    routing(controller.routes())
}
