package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.quotes.QuoteService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class CatalogController(
    private val catalogService: CatalogService,
    private val quoteService: QuoteService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/tickers") {
            get {
                call.respond(HttpStatusCode.OK, catalogService.fetchAll())
            }

            post("/refresh") {
                call.respond(
                    HttpStatusCode.OK,
                    TickerRefreshResult(catalog = catalogService.refresh(), quotes = quoteService.refreshEod()),
                )
            }

            get("/{ticker}") {
                call.respondOr404(catalogService.fetchBy(call.requiredString("ticker")))
            }

            // The owner's manual classification, outranking the BDI-derived class. PUT sets it,
            // DELETE hands the ticker back to the catalog; both return the updated catalog row.
            put("/{ticker}/classification") {
                val payload = call.receive<TickerClassification>()
                call.respondOr404(
                    catalogService.classify(call.requiredString("ticker"), payload.assetClass),
                )
            }

            delete("/{ticker}/classification") {
                call.respondOr404(catalogService.revertClassification(call.requiredString("ticker")))
            }

            get("/{ticker}/quotes") {
                call.respond(HttpStatusCode.OK, quoteService.fetchAllBy(call.requiredString("ticker")))
            }
        }
    }
}

// Mirrored in frontend/src/api/types.ts (TickerClassification).
private data class TickerClassification(
    val assetClass: AssetClass,
)

// Mirrored in frontend/src/api/types.ts (TickerRefreshResult).
private data class TickerRefreshResult(
    val catalog: Int,
    val quotes: Int,
)
