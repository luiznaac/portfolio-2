package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.brokeragenote.BrokerageNoteService
import dev.agner.portfolio.usecase.brokeragenote.model.ImportedTradeConfirmation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.toByteArray
import org.springframework.stereotype.Component

@Component
class BrokerageNoteController(
    private val service: BrokerageNoteService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/notes/import") {
            // Raw XLSX body, not JSON — same reasoning as StrategyController's /reports: read
            // directly instead of going through ContentNegotiation.
            post("/preview") {
                val xlsxBytes = call.receiveChannel().toByteArray()

                call.respond(HttpStatusCode.OK, service.preview(xlsxBytes))
            }

            post("/confirm") {
                val payload = call.receive<List<ImportedTradeConfirmation>>()

                call.respond(HttpStatusCode.Created, service.confirm(payload))
            }
        }
    }
}
