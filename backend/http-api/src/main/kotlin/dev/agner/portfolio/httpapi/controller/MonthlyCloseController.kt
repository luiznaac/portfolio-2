package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.monthlyclose.MonthlyCloseService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class MonthlyCloseController(
    private val service: MonthlyCloseService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/monthly-close") {
            get("/current") {
                call.respond(HttpStatusCode.OK, service.current())
            }

            get("/history") {
                call.respond(HttpStatusCode.OK, service.history())
            }

            post("/close") {
                call.respond(HttpStatusCode.OK, service.close())
            }

            get("/drift-alert") {
                call.respond(HttpStatusCode.OK, service.driftAlert())
            }
        }
    }
}
