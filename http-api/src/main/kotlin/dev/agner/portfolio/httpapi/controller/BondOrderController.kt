package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.consolidation.BondConsolidator
import dev.agner.portfolio.usecase.bond.model.BondOrderType
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component

@Component
class BondOrderController(
    private val service: BondOrderService,
    private val yieldService: BondConsolidator,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/bonds/orders") {
            post {
                val payload = call.receive<BondOrderCreationRequest>()

                call.respond(
                    HttpStatusCode.Created,
                    service.create(payload.bondId, payload.type, payload.date, payload.amount)
                )
            }

            get {
                call.respond(service.fetchAll())
            }

            post("/{bond_order_id}/consolidate") {
                val bondOrderId = call.parameters["bond_order_id"]!!.toInt()

                yieldService.consolidateBy(bondOrderId)
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private data class BondOrderCreationRequest(
    val bondId: Int,
    val type: BondOrderType,
    val date: LocalDate,
    val amount: Double,
)
