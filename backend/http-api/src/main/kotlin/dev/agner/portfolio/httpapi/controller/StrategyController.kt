package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class StrategyController(
    private val service: StrategyService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/strategies") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            post {
                val payload = call.receive<StrategyCreation>()

                call.respond(HttpStatusCode.Created, service.create(payload))
            }
        }
    }
}
