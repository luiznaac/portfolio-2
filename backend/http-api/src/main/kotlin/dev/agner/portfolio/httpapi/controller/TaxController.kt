package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.tax.HarvestService
import dev.agner.portfolio.usecase.tax.StockTaxService
import dev.agner.portfolio.usecase.tax.model.HarvestOptOutUpdate
import dev.agner.portfolio.usecase.tax.model.HarvestPairCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The open month's tax surface: the R$20k stock-sale meter, the fiscal average price of every held
// ticker and the DARF 6015 deadline; plus the harvest pass — the suggestions read, the pair
// acceptance and the per-ticker opt-out. "No open month" is the 404 every current-month read
// promises.
@Component
class TaxController(
    private val service: StockTaxService,
    private val harvest: HarvestService,
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

        route("/tax/harvest") {
            get {
                call.respondOr404(harvest.fetch())
            }

            post {
                val payload = call.receive<HarvestPairCreation>()
                val report = harvest.accept(payload)
                if (report == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.Created, report)
                }
            }

            put("/{ticker}/opt-out") {
                val payload = call.receive<HarvestOptOutUpdate>()
                call.respondOr404(harvest.setOptOut(call.requiredString("ticker"), payload.optedOut))
            }
        }
    }
}
