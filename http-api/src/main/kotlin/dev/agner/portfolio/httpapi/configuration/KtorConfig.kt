package dev.agner.portfolio.httpapi.configuration

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty
import io.ktor.server.routing.Routing
import io.ktor.server.routing.routing
import org.springframework.stereotype.Component

@Component
class KtorConfig(
    private val routes: Set<Routing.() -> Unit>
) {

    fun start() {
        println(" Starting KTOR ")
        embeddedServer(Netty, port = 8080) {
            routes.onEach {
                println(" Init route: $it ")
                routing(it)
            }
        }.start(wait = true)
    }
}
