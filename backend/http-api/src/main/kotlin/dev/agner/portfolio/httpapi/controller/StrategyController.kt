package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.strategy.InvalidStrategyIdException
import dev.agner.portfolio.usecase.strategy.StrategyEditionService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.ApplicationCall
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
                    val strategyId = call.strategyId()

                    call.respond(HttpStatusCode.OK, editionService.fetchEditions(strategyId))
                }

                // Raw PDF body, not JSON — the broker's model-portfolio report. Read directly instead
                // of going through ContentNegotiation, which already has a PDF converter
                // registered for the brokerage-note upload flow (UploadController) that expects a
                // different shape.
                post("/reports") {
                    val strategyId = call.strategyId()
                    val pdfBytes = call.receiveChannel().toByteArray()

                    call.respond(HttpStatusCode.Created, editionService.importReport(strategyId, pdfBytes))
                }
            }
        }
    }
}

// Client-driven path parsing: an absent or non-numeric segment must surface as the domain error
// (mapped to 400) instead of a NumberFormatException/KotlinNullPointerException 500.
internal fun ApplicationCall.strategyId(): Int =
    parameters["strategy_id"]?.toIntOrNull()
        ?: throw InvalidStrategyIdException(parameters["strategy_id"])
