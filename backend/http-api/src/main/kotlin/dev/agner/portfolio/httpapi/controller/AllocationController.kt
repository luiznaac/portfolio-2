package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.portfolio.AllocationService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The month-scoped allocation read: the whole Capital → class → strategy/sub-class → ticker tree,
// computed on read from the open month's targets, positions, quotes and RF consolidations.
@Component
class AllocationController(
    private val service: AllocationService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/month") {
            get("/current/allocation") {
                val allocation = service.fetchCurrent()
                if (allocation == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, allocation)
                }
            }
        }
    }
}
