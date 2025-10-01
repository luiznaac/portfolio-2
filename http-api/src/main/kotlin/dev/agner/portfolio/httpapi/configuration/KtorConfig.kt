package dev.agner.portfolio.httpapi.configuration

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.controller.ControllerTemplate
import dev.agner.portfolio.usecase.extension.logger
import io.ktor.http.ContentType
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpMethod
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.install
import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.cors.routing.CORS
import io.ktor.server.routing.routing
import org.springframework.stereotype.Component

@Component
class KtorConfig(
    private val routes: Set<ControllerTemplate>,
    private val mapper: ObjectMapper,
) {

    private val log = logger()

    fun start() {
        log.info("Starting ktor")

        embeddedServer(Netty, port = 8080) {
            routes.onEach {
                log.info("Initializing route: ${it::class.java.simpleName}")
                routing(it.routes())
            }

            install(ContentNegotiation) {
                register(ContentType.Application.Json, JacksonConverter(mapper))
            }

            install(CORS) {
                allowMethod(HttpMethod.Options)
                allowMethod(HttpMethod.Put)
                allowMethod(HttpMethod.Delete)
                allowMethod(HttpMethod.Patch)
                allowHeader(HttpHeaders.Authorization)
                allowHeader(HttpHeaders.ContentType)
                anyHost()
            }
        }.start(wait = true)
    }
}
