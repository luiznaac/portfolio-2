package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkSeriesStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The stored benchmark series: its freshness and the manual re-check the data panel offers, plus the
// one-off backfill — a curl/runbook action, deliberately without a scheduler.
@Component
class BenchmarkController(
    private val store: BenchmarkSeriesStore,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/benchmarks") {
            get {
                call.respond(HttpStatusCode.OK, store.status())
            }

            post("/refresh") {
                call.respond(HttpStatusCode.OK, store.refresh())
            }

            post("/backfill") {
                call.respond(HttpStatusCode.OK, store.backfill(call.requiredDate("from")))
            }
        }
    }
}
