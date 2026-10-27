package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.portfolio.AllocationService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The allocation read: the whole Capital → class → strategy/sub-class → ticker tree, computed on
// read from the open month's targets, positions, quotes and RF consolidations. It is month-free —
// without an open month it still serves the Actual-only tree, with no `period`.
@Component
class AllocationController(
    private val service: AllocationService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/month") {
            get("/current/allocation") {
                call.respond(HttpStatusCode.OK, service.fetchCurrent())
            }
        }
    }
}
