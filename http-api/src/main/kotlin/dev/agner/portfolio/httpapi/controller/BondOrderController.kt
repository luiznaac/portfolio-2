package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.model.BondOrderCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class BondOrderController(
    private val service: BondOrderService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/bonds/orders") {
            post {
                val payload = call.receive<BondOrderCreation>()

                call.respond(HttpStatusCode.Created, service.create(payload))
            }

            get {
                call.respond(service.fetchAll())
            }
        }
    }
}
