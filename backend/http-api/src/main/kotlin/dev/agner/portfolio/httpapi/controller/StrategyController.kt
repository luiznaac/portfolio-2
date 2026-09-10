package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.strategyreport.StrategyReportParserResolver
import dev.agner.portfolio.usecase.strategy.StrategyEditionService
import dev.agner.portfolio.usecase.strategy.StrategyService
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyWeightCreation
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
    private val parserResolver: StrategyReportParserResolver,
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

            get("/weights") {
                call.respond(HttpStatusCode.OK, service.fetchWeightHistory())
            }

            route("/{strategy_id}") {
                get("/editions") {
                    val strategyId = call.parameters["strategy_id"]!!.toInt()

                    call.respond(HttpStatusCode.OK, editionService.fetchEditions(strategyId))
                }

                post("/weight") {
                    val strategyId = call.parameters["strategy_id"]!!.toInt()
                    val payload = call.receive<StrategyWeightCreation>()

                    call.respond(HttpStatusCode.Created, service.setWeight(strategyId, payload))
                }

                // Raw PDF body, not JSON — the broker's model-portfolio report. Read directly
                // instead of going through ContentNegotiation, whose PDF converter is registered
                // for the upload flow and expects a different shape. Parsing happens here so the
                // domain only ever receives the extracted report.
                post("/reports") {
                    val strategyId = call.parameters["strategy_id"]!!.toInt()
                    val pdfBytes = call.receiveChannel().toByteArray()
                    val report = parserResolver.parse(pdfBytes)

                    call.respond(HttpStatusCode.Created, editionService.importReport(strategyId, report))
                }
            }
        }
    }
}
