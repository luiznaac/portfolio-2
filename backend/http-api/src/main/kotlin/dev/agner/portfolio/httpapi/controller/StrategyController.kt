package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.import.strategy.StrategyReportParserResolver
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.EditionActivation
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The strategy catalog and its per-strategy editions: create/read strategies, import a monthly XP
// report into a staged edition (with its diff), and confirm a staged edition after review. The
// service owns the staged/active lifecycle and the "one edition per strategy × month" rule; this
// controller maps "unknown strategy or month" to 404 and a rejected report to the canonical 400.
@Component
class StrategyController(
    private val service: StrategyService,
    private val parsers: StrategyReportParserResolver,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/strategies") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            post {
                val payload = call.receive<StrategyCreation>()
                call.respond(HttpStatusCode.Created, service.create(payload))
            }

            route("/{strategy_id}/editions") {
                get {
                    val editions = service.fetchEditions(call.requiredInt("strategy_id"))
                    if (editions == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(HttpStatusCode.OK, editions)
                    }
                }

                post("/import") {
                    val upload = call.receiveUpload()
                    val report = parsers.resolve(upload.fileName, upload.content)
                    val imported = service.importEdition(call.requiredInt("strategy_id"), report)
                    if (imported == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(HttpStatusCode.Created, imported)
                    }
                }

                post {
                    val payload = call.receive<EditionActivation>()
                    val edition = service.activateEdition(
                        call.requiredInt("strategy_id"),
                        payload.referenceMonth,
                        payload.entries,
                    )
                    if (edition == null) {
                        call.respond(HttpStatusCode.NotFound)
                    } else {
                        call.respond(HttpStatusCode.OK, edition)
                    }
                }
            }
        }
    }
}
