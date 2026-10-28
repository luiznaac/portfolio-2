package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.import.B3PositionParser
import dev.agner.portfolio.usecase.history.BackfillService
import dev.agner.portfolio.usecase.listedasset.GapService
import dev.agner.portfolio.usecase.listedasset.UnsupportedPositionFileException
import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
import dev.agner.portfolio.usecase.listedasset.model.GapPriceUpdate
import dev.agner.portfolio.usecase.listedasset.model.OpeningSeedCreation
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The history edge. The backfill command replays the OPENING seeds and the GAP records; a Posição
// export may ride along as the end-of-window custody cross-check, and the body is optional so a run
// can absorb seeds alone. Openings create the adoption seeds; GAPs are the attention list the owner
// works through, with a missing sale's proceeds editable.
@Component
class HistoryController(
    private val service: BackfillService,
    private val positionParser: B3PositionParser,
    private val gaps: GapService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/history") {
            post("/backfill") {
                call.respond(HttpStatusCode.OK, service.backfill(call.optionalCustody()))
            }

            // The adoption write the absorption reads from: one seed per held ticker at its closing
            // price. Idempotency is the caller's (the backfill absorbs a seed once history covers it).
            post("/openings") {
                call.respond(HttpStatusCode.OK, service.seedOpenings(call.receive<List<OpeningSeedCreation>>()))
            }
        }

        route("/gaps") {
            get {
                call.respond(HttpStatusCode.OK, gaps.fetchAll())
            }

            post("/{gap_id}/price") {
                val update = call.receive<GapPriceUpdate>()
                call.respondOr404(gaps.price(call.requiredInt("gap_id"), update.unitPrice))
            }
        }
    }

    // A multipart body is the B3 Posição export; any other content type means "absorb only". The
    // parser claims the file by content, so a wrong upload fails loudly instead of silently running
    // without a cross-check.
    private suspend fun ApplicationCall.optionalCustody(): List<CustodyPosition> {
        val contentType = request.headers[HttpHeaders.ContentType]
        if (contentType == null || !contentType.startsWith(MULTIPART)) return emptyList()

        val upload = receiveUpload()
        if (!positionParser.shouldExecute(upload.fileName, upload.content)) {
            throw UnsupportedPositionFileException(upload.fileName)
        }
        return positionParser.parse(upload.fileName, upload.content)
    }

    private companion object {
        const val MULTIPART = "multipart/form-data"
    }
}
