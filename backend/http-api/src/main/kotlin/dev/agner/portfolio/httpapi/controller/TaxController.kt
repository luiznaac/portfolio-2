package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.tax.capitalgains.CapitalGainsService
import dev.agner.portfolio.usecase.tax.stepup.StepUpService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class TaxController(
    private val capitalGainsService: CapitalGainsService,
    private val stepUpService: StepUpService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/tax") {
            get("/capital-gains") {
                call.respond(HttpStatusCode.OK, capitalGainsService.monthlyReport())
            }
            get("/step-up-plan") {
                call.respond(HttpStatusCode.OK, stepUpService.plan())
            }
        }
    }
}
