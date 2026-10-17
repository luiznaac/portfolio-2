package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.MonthCreation
import dev.agner.portfolio.usecase.portfolio.model.MonthUpdate
import dev.agner.portfolio.usecase.portfolio.model.Targets
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The month-scoped kernel: opening a month, reading/updating the current one, and its target chain.
// The single open month is the repository's invariant; this controller only maps "no open month" to
// the 404 the current-month reads promise.
@Component
class MonthController(
    private val service: MonthService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/month") {
            post {
                val payload = call.receive<MonthCreation>()
                call.respond(HttpStatusCode.Created, service.open(payload))
            }

            get("/current") {
                val current = service.fetchCurrent()
                if (current == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, current)
                }
            }

            post("/current") {
                val payload = call.receive<MonthUpdate>()
                val current = service.updateCurrent(payload)
                if (current == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, current)
                }
            }

            get("/current/targets") {
                val targets = service.fetchCurrentTargets()
                if (targets == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, targets)
                }
            }

            put("/current/targets") {
                val payload = call.receive<Targets>()
                val current = service.replaceCurrentTargets(payload)
                if (current == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, current)
                }
            }
        }
    }
}
