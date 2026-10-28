package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.monthlyclose.MonthlyCloseService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The monthly close's edge: the close action itself and the closed-month list history navigation
// starts from. A close with no open month is the same 404 the current-month reads promise; an
// undecided transfer inbox surfaces as the 409 the domain exception handler renders.
@Component
class MonthlyCloseController(
    private val service: MonthlyCloseService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/month") {
            post("/current/close") {
                val snapshot = service.closeCurrent()
                if (snapshot == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, snapshot)
                }
            }
        }

        get("/months") {
            call.respond(HttpStatusCode.OK, service.fetchClosedMonths())
        }

        get("/months/{month_id}") {
            call.respondOr404(service.fetchSnapshot(call.requiredInt("month_id")))
        }
    }
}
