package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.performance.benchmark.BenchmarkSeriesStore
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The one-off benchmark backfill: a curl/runbook action, deliberately without a UI or a scheduler.
// It seeds (or repairs) the stored series from a provider's earliest available date to today.
@Component
class BenchmarkController(
    private val store: BenchmarkSeriesStore,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/benchmarks") {
            post("/backfill") {
                call.respond(HttpStatusCode.OK, store.backfill(call.requiredDate("from")))
            }
        }
    }
}
