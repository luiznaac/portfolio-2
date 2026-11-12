package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.portfolio.PortfolioStatusService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import org.springframework.stereotype.Component

// The landing's single aggregate read. It is deliberately the portfolio's standing status, not a
// screen name: the status strip, the month line, the data-state panel and the attention queue all
// read this one route. No sentence crosses the wire — the frontend owns the copy.
@Component
class PortfolioStatusController(
    private val service: PortfolioStatusService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        get("/portfolio/status") {
            call.respond(HttpStatusCode.OK, service.fetch())
        }
    }
}
