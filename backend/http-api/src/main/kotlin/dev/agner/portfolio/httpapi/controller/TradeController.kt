package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerCursor
import dev.agner.portfolio.usecase.listedasset.model.TradeLedgerFilter
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class TradeController(
    private val service: TradeService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/trades") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            // The paged, filtered ledger read. GET /trades stays the detail views' full list; this
            // route narrows and pages at the database instead of shipping the whole book.
            get("/ledger") {
                val cursor = call.request.queryParameters["cursor"]
                val filter = TradeLedgerFilter(
                    ticker = call.request.queryParameters["ticker"],
                    from = call.optionalDate("from"),
                    to = call.optionalDate("to"),
                    side = call.optionalSide(),
                    after = cursor?.let {
                        TradeLedgerCursor.decode(it) ?: throw InvalidParameterException("cursor", it)
                    },
                )
                call.respond(HttpStatusCode.OK, service.fetchLedger(filter))
            }

            // Manual entries for intra-month or fallback records. Source and ordinal are decided by
            // the service, not the body — a manual trade that dupes an import is refused.
            post {
                val payload = call.receive<TradeCreation>()
                call.respond(HttpStatusCode.Created, service.createManual(payload))
            }
        }
    }
}

private fun ApplicationCall.optionalSide(): TradeSide? =
    request.queryParameters["side"]?.let { raw ->
        runCatching { TradeSide.valueOf(raw.uppercase()) }.getOrNull()
            ?: throw InvalidParameterException("side", raw)
    }
