package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.income.parser.IIncomeStatementParser
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.toByteArray
import org.springframework.stereotype.Component

@Component
class IncomeController(
    private val service: IncomeService,
    private val parser: IIncomeStatementParser,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/income") {
            get("/summary") {
                call.respond(HttpStatusCode.OK, service.summary())
            }

            get("/assets/{listed_asset_id}") {
                val assetId = call.requiredInt("listed_asset_id")

                call.respond(HttpStatusCode.OK, service.eventsForAsset(assetId))
            }

            // Raw XLSX body, not JSON — same reasoning as the brokerage-note import.
            post("/reconcile") {
                val xlsxBytes = call.receiveChannel().toByteArray()
                val received = parser.parse(xlsxBytes)

                call.respond(HttpStatusCode.OK, service.reconcile(received))
            }
        }
    }
}
