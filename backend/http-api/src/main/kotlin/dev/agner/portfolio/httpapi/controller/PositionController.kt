package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.import.B3PositionParser
import dev.agner.portfolio.usecase.listedasset.PositionReconciliationService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.UnsupportedPositionFileException
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class PositionController(
    private val positionService: PositionService,
    private val reconciliationService: PositionReconciliationService,
    private val parser: B3PositionParser,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/positions") {
            get {
                call.respond(HttpStatusCode.OK, positionService.fetchAll())
            }

            // The Ativos detail read: the stored projection plus the ledger it was replayed from.
            get("/{ticker}") {
                val detail = positionService.fetchDetail(call.requiredString("ticker"))
                if (detail == null) {
                    call.respond(HttpStatusCode.NotFound)
                } else {
                    call.respond(HttpStatusCode.OK, detail)
                }
            }

            // The repair path: replay every ticker's ledger into its position. A command, so it is
            // a POST rather than a write hidden behind a GET.
            post("/reproject") {
                call.respond(HttpStatusCode.OK, positionService.reprojectAll())
            }

            // The monthly custody cross-check. Read only: a drift is returned as attention and
            // never edits the ledger.
            post("/reconcile") {
                val upload = call.receiveUpload()
                if (!parser.shouldExecute(upload.fileName, upload.content)) {
                    throw UnsupportedPositionFileException(upload.fileName)
                }
                val custody = parser.parse(upload.fileName, upload.content)
                call.respond(HttpStatusCode.OK, reconciliationService.reconcile(custody))
            }
        }
    }
}
