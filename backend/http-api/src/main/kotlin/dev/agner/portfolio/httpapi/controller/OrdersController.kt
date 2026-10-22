package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.order.OrderLifecycleService
import dev.agner.portfolio.usecase.order.model.OrderMatchUpdate
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The order lifecycle's edge: the persisted plan read, the explicit recalculation write, the
// transfer inbox decisions and the per-order match override. Reads never recompute; a refresh is
// the one write that does. "No open month" is the 404 every current-month read promises.
@Component
class OrdersController(
    private val service: OrderLifecycleService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/orders") {
            get {
                call.respondOr404(service.fetch())
            }

            post("/plan/refresh") {
                call.respondOr404(service.refresh())
            }

            post("/transfers/{id}/approve") {
                call.respondOr404(service.approveTransfer(call.requiredInt("id")))
            }

            post("/transfers/{id}/reject") {
                call.respondOr404(service.rejectTransfer(call.requiredInt("id")))
            }

            put("/{order_id}/match") {
                val payload = call.receive<OrderMatchUpdate>()
                call.respondOr404(service.overrideMatch(call.requiredInt("order_id"), payload.tradeId))
            }
        }
    }
}

// The three reads share one "absent is a 404" contract; the generic keeps the response types from
// leaking a hand-rolled helper per shape.
private suspend inline fun <reified T : Any> ApplicationCall.respondOr404(value: T?) =
    if (value == null) respond(HttpStatusCode.NotFound) else respond(HttpStatusCode.OK, value)
