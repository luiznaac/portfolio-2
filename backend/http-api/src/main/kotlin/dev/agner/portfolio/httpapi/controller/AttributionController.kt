package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.listedasset.AttributionService
import dev.agner.portfolio.usecase.listedasset.LegacyAttributionService
import dev.agner.portfolio.usecase.listedasset.model.AttributionSliceCreation
import dev.agner.portfolio.usecase.listedasset.model.AttributionSummary
import dev.agner.portfolio.usecase.listedasset.model.AttributionTransferCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The attribution ledger for one listed asset: GET reads the slices and remainder, POST upserts a
// strategy's slice, POST .../transfer moves units between slices without an open month, DELETE
// removes a slice. The ticker is normalized in the service, so an unknown position or strategy on a
// write is a null result mapped to 404, while a slice above the position's custody or an invalid
// transfer is a 400 domain error. Deleting a slice that does not exist is a no-op.
//
// The page-level bulk legacy action lives beside it: a dry-run read and a per-Ticker apply that fans
// out over every held outside Position. It is not ticker-addressed, so it sits on its own path.
@Component
class AttributionController(
    private val service: AttributionService,
    private val legacyService: LegacyAttributionService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/positions/{ticker}/attribution") {
            get {
                call.respondSummary(service.fetchSummary(call.requiredString("ticker")))
            }

            post {
                val payload = call.receive<AttributionSliceCreation>()
                call.respondSummary(service.setSlice(call.requiredString("ticker"), payload))
            }

            // The month-free manual transfer: it applies immediately and writes no inbox row.
            post("/transfer") {
                val payload = call.receive<AttributionTransferCreation>()
                call.respondSummary(service.transfer(call.requiredString("ticker"), payload))
            }

            delete("/{strategy_id}") {
                call.respondSummary(
                    service.removeSlice(call.requiredString("ticker"), call.requiredInt("strategy_id")),
                )
            }
        }

        route("/positions/legacy-attribution") {
            get("/preview") {
                call.respond(HttpStatusCode.OK, legacyService.preview())
            }

            post {
                call.respond(HttpStatusCode.OK, legacyService.apply())
            }
        }
    }

    private suspend fun ApplicationCall.respondSummary(summary: AttributionSummary?) =
        if (summary == null) respond(HttpStatusCode.NotFound) else respond(HttpStatusCode.OK, summary)
}
