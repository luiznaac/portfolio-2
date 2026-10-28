package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.OrderPlanPhase
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The month-scoped order plan read: the ideal chain and its leaf candidates, computed on read from
// the open month's Capital, Floor and Targets, the projected positions and the latest closes; the
// funding phase adds the sells that cover the buys, and the full phase adds the pro-rata scaling,
// the attribution bias and transfers, and the RF sub-class orders. The phase query defaults to
// `ideals`.
@Component
class OrderPlanController(
    private val service: OrderPlanService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/month") {
            get("/current/orders") {
                val phase = OrderPlanPhase.from(call.request.queryParameters["phase"])
                val plan = service.computePlan(phase)
                if (plan == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, plan)
                }
            }
        }
    }
}
