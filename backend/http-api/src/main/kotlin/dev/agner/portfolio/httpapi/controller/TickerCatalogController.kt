package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.commons.logger
import dev.agner.portfolio.usecase.listedasset.catalog.TickerCatalogSyncService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.springframework.stereotype.Component

@Component
class TickerCatalogController(
    private val service: TickerCatalogSyncService,
) : ControllerTemplate {

    init {
        // This app has no working "app is ready" hook to sync the catalog from: KtorConfig's
        // constructor blocks the main thread for the process's entire lifetime
        // (embeddedServer(...).start(wait = true)), so Spring's context refresh never reaches
        // ApplicationReadyEvent. This controller is a guaranteed dependency of KtorConfig (it's
        // part of the injected `routes` set), so its own construction — which necessarily
        // happens before KtorConfig's — is the earliest reliable point to fire off a one-time
        // background sync without blocking startup.
        CoroutineScope(Dispatchers.IO).launch {
            val count = service.syncIfEmpty()
            if (count > 0) logger().info("Synced $count tickers into the local catalog")
        }
    }

    override fun routes(): RouteDefinition = {
        route("/ticker-catalog") {
            get("/search") {
                val query = call.request.queryParameters["q"].orEmpty()
                call.respond(HttpStatusCode.OK, if (query.isBlank()) emptyList() else service.search(query))
            }

            // Re-run on demand to pick up newly listed tickers (IPOs, new FIIs/ETFs) — upsert is
            // additive and idempotent, safe to call repeatedly.
            post("/sync") {
                call.respond(HttpStatusCode.OK, mapOf("count" to service.sync()))
            }
        }
    }
}
