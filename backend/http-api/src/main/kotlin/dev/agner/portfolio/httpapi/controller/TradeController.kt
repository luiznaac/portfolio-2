package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class TradeController(
    private val service: TradeService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/trades") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            // Manual entries for intra-month or fallback records. Source and ordinal are decided by
            // the service, not the body — a manual trade that dupes an import is refused.
            post {
                val payload = call.receive<TradeCreation>()
                call.respond(HttpStatusCode.Created, service.createManual(payload))
            }
        }
    }
}
