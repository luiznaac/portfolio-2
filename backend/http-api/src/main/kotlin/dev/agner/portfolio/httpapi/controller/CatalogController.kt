package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.catalog.ClassificationQueueService
import dev.agner.portfolio.usecase.catalog.TickerRefreshService
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
    private val tickerRefreshService: TickerRefreshService,
    private val classificationQueueService: ClassificationQueueService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/tickers") {
            get {
                call.respond(HttpStatusCode.OK, catalogService.fetchAll())
            }

            // One action owns both halves: catalog and quotes from the same daily file, plus the
            // ledger's historical closes. A partial failure is still a 200 carrying `failed`.
            post("/refresh") {
                call.respond(HttpStatusCode.OK, tickerRefreshService.refresh())
            }

            // The held-unclassified classification queue the bulk modal lists: ticker, short name,
            // quantity and the named reason it is still classless.
            get("/unclassified") {
                call.respond(HttpStatusCode.OK, classificationQueueService.fetchQueue())
            }

            get("/{ticker}") {
                call.respondOr404(catalogService.fetchBy(call.requiredString("ticker")))
            }

            // The bulk classification the held-unclassified modal submits: only the chosen rows
            // travel, the whole batch is one atomic write, and the response carries every updated
            // catalog row.
            put("/classification") {
                val payload = call.receive<TickerBatchClassification>()
                call.respond(HttpStatusCode.OK, catalogService.classifyAll(payload.classifications))
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

// Mirrored in frontend/src/api/types.ts (TickerBatchClassification): ticker → chosen class, so only
// the rows the owner picked in the bulk modal travel.
private data class TickerBatchClassification(
    val classifications: Map<String, AssetClass>,
)
