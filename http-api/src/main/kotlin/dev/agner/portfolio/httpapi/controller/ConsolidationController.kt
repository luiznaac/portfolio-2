package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.consolidation.ConsolidationService
import dev.agner.portfolio.usecase.consolidation.ProductType
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class ConsolidationController(
    private val service: ConsolidationService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/consolidations") {
            post("/schedule") {
                call.respond(HttpStatusCode.OK, service.scheduleConsolidations())
            }

            post("/{product_type}/{product_id}") {
                val productType = ProductType.valueOf(call.parameters["product_type"]!!)
                val productId = call.parameters["product_id"]!!.toInt()

                service.consolidateProduct(productId, productType)

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
