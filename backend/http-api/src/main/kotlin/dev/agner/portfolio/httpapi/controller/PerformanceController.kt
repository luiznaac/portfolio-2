package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.performance.PerformanceService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The performance edge: one closed month's report and the whole historical series. A month that was
// never closed has no snapshot, so the month read is the same 404 the other month-scoped reads use.
@Component
class PerformanceController(
    private val service: PerformanceService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/months/{month_id}") {
            get("/performance") {
                call.respondOr404(service.fetchForMonth(call.requiredInt("month_id")))
            }
        }

        get("/performance/series") {
            call.respond(HttpStatusCode.OK, service.fetchSeries())
        }
    }
}
