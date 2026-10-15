package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.catalog.CatalogService
import dev.agner.portfolio.usecase.quotes.QuoteService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
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
                val entry = catalogService.fetchBy(call.requiredString("ticker"))
                when (entry) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(HttpStatusCode.OK, entry)
                }
            }

            get("/{ticker}/quotes") {
                call.respond(HttpStatusCode.OK, quoteService.fetchAllBy(call.requiredString("ticker")))
            }

            post("/{ticker}/quotes/refresh") {
                val quote = quoteService.refreshCurrent(call.requiredString("ticker"))
                when (quote) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(HttpStatusCode.OK, quote)
                }
            }
        }
    }
}

// Mirrored in frontend/src/api/types.ts (TickerRefreshResult).
private data class TickerRefreshResult(
    val catalog: Int,
    val quotes: Int,
)
