package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.consolidation.ProductType
import dev.agner.portfolio.usecase.schedule.ScheduleService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class ConsolidationController(
    private val service: ScheduleService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/consolidation") {
            post("/{product_type}/schedule") {
                val productType = ProductType.valueOf(call.parameters["product_type"]!!)

                service.scheduleProductConsolidation(productType)

                call.respond(HttpStatusCode.OK)
            }
        }
    }
}
