package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.strategy.StrategyEditionService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.request.receiveChannel
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import io.ktor.utils.io.toByteArray
import org.springframework.stereotype.Component

@Component
class StrategyController(
    private val service: StrategyService,
    private val editionService: StrategyEditionService,
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

            route("/{strategy_id}") {
                get("/editions") {
                    val strategyId = call.parameters["strategy_id"]!!.toInt()

                    call.respond(HttpStatusCode.OK, editionService.fetchEditions(strategyId))
                }

                // Raw PDF body, not JSON — the broker's model-portfolio report. Read directly instead
                // of going through ContentNegotiation, which already has a PDF converter
                // registered for the brokerage-note upload flow (UploadController) that expects a
                // different shape.
                post("/reports") {
                    val strategyId = call.parameters["strategy_id"]!!.toInt()
                    val pdfBytes = call.receiveChannel().toByteArray()

                    call.respond(HttpStatusCode.Created, editionService.importReport(strategyId, pdfBytes))
                }
            }
        }
    }
}
