package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.bond.BondService
import dev.agner.portfolio.usecase.bond.model.BondCreation.FixedRateBondCreation
import dev.agner.portfolio.usecase.bond.model.BondCreation.FloatingRateBondCreation
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.consolidation.ConsolidationService
import dev.agner.portfolio.usecase.consolidation.ProductType.BOND
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
    private val consolidationService: ConsolidationService,
    private val positionService: BondPositionService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/bonds") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            post("/fixed") {
                val payload = call.receive<FixedRateBondCreation>()

                call.respond(
                    HttpStatusCode.Created,
                    service.create(payload),
                )
            }

            post("/floating") {
                val payload = call.receive<FloatingRateBondCreation>()

                call.respond(
                    HttpStatusCode.Created,
                    service.create(payload),
                )
            }

            post("/{bond_id}/consolidate") {
                val bondId = call.parameters["bond_id"]!!.toInt()

                consolidationService.consolidateProduct(bondId, BOND)
                call.respond(HttpStatusCode.NoContent)
            }

            get("/{bond_id}/positions") {
                val bondId = call.parameters["bond_id"]!!.toInt()

                call.respond(positionService.calculateByBondId(bondId))
            }
        }
    }
}
