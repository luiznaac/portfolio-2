package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.tax.StockTaxService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The open month's tax surface: the R$20k stock-sale meter and the fiscal average price of every
// held ticker. A pure read — nothing is stored — and "no open month" is the 404 every
// current-month read promises.
@Component
class TaxController(
    private val service: StockTaxService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/month/current") {
            get("/tax") {
                val report = service.fetchCurrent()
                if (report == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, report)
                }
            }
        }
    }
}
