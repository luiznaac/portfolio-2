package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.configuration.DefaultDomainExceptionStatusMapper
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.configuration.JsonMapper
import io.ktor.http.ContentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.routing.routing

// Installs a controller the way KtorConfig does — JSON via the shared mapper and the domain
// exception -> ApiError mapping — so a controller test observes the real edge contract.
internal fun Application.installRoutes(controller: ControllerTemplate) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
    }

    install(StatusPages) {
        exception<DomainException> { call, cause ->
            call.respond(
                DefaultDomainExceptionStatusMapper().statusFor(cause),
                TestApiError(error = cause.error, message = cause.userMessage, detail = cause.detail),
            )
        }
    }

    routing(controller.routes())
}

internal data class TestApiError(val error: String, val message: String, val detail: String)
