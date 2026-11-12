package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.import.B3IncomeParser
import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.income.UnsupportedIncomeFileException
import dev.agner.portfolio.usecase.income.model.IncomeQuery
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate
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
                call.respond(HttpStatusCode.OK, service.fetchAll(call.incomeQuery()))
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

// The ledger's additive filters. A blank value is no narrowing, matching how the frontend omits an
// untouched control; a malformed date or an unknown lifecycle stage is the caller's error (400),
// never a 500.
private fun ApplicationCall.incomeQuery(): IncomeQuery {
    val params = request.queryParameters
    return IncomeQuery(
        from = optionalQueryDate("from"),
        to = optionalQueryDate("to"),
        ticker = params["ticker"]?.trim()?.takeIf { it.isNotEmpty() },
        status = params["status"]?.trim()?.takeIf { it.isNotEmpty() }?.let { raw ->
            IncomeStatus.entries.firstOrNull { it.name == raw }
                ?: throw InvalidParameterException("status", raw)
        },
    )
}

private fun ApplicationCall.optionalQueryDate(name: String): LocalDate? =
    if (request.queryParameters[name]?.isNotBlank() == true) requiredDate(name) else null
