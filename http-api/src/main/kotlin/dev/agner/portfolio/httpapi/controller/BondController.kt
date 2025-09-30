package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.bond.BondService
import dev.agner.portfolio.usecase.index.model.IndexId
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class BondController(
    private val service: BondService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/bonds") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            post("/fixed") {
                val payload = call.receive<FixedRateBondRequest>()

                call.respond(
                    HttpStatusCode.Created,
                    service.createFixedRateBond(payload.name, payload.value),
                )
            }

            post("/floating") {
                val payload = call.receive<FloatingRateBondRequest>()

                call.respond(
                    HttpStatusCode.Created,
                    service.createFloatingRateBond(payload.name, payload.value, payload.indexId),
                )
            }
        }
    }
}

private data class FixedRateBondRequest(
    val name: String,
    val value: Double,
)

private data class FloatingRateBondRequest(
    val name: String,
    val value: Double,
    val indexId: IndexId,
)
