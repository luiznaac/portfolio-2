package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.import.B3IncomeParser
import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.income.UnsupportedIncomeFileException
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The Proventos surface: the stored income ledger (with its lifecycle derived on read), the
// upcoming-forecast read, and the "Proventos a Receber" import. A GET never writes — the import is
// its own POST route, and a file that is not a proventos export is refused with the canonical 400.
@Component
class IncomeController(
    private val service: IncomeService,
    private val parser: B3IncomeParser,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/income") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            // The forecast: movements announced or payable, never the ones already received.
            get("/upcoming") {
                call.respond(HttpStatusCode.OK, service.fetchUpcoming())
            }

            post("/import") {
                val upload = call.receiveUpload()
                if (!parser.shouldExecute(upload.fileName, upload.content)) {
                    throw UnsupportedIncomeFileException(upload.fileName)
                }
                val events = parser.parse(upload.fileName, upload.content)
                call.respond(HttpStatusCode.Created, service.importEvents(events))
            }
        }
    }
}
