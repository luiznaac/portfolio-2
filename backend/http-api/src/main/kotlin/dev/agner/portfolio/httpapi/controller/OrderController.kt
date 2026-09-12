package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.order.OrderPlanService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import kotlinx.datetime.LocalDate
import org.springframework.stereotype.Component
import java.math.BigDecimal

@Component
class OrderController(
    private val planService: OrderPlanService,
    private val attributionService: AttributionService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/orders") {
            get("/plan") {
                call.respond(HttpStatusCode.OK, planService.computePlan())
            }

            // A TransferSuggestion applied directly, rather than going through a proposal's own
            // approve/reject lifecycle (see TransferSuggestion's doc comment) — two attribution
            // movements, reason TRANSFERENCIA, both saved in one transaction by the service: it's
            // bookkeeping, not a trade, and either both sides land or neither does.
            post("/transfers/apply") {
                val payload = call.receive<ApplyTransferRequest>()

                attributionService.transferBetweenStrategies(
                    listedAssetId = payload.listedAssetId,
                    fromStrategyId = payload.fromStrategyId,
                    toStrategyId = payload.toStrategyId,
                    quantity = payload.quantity,
                    date = payload.date,
                )

                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}

private data class ApplyTransferRequest(
    val listedAssetId: Int,
    val fromStrategyId: Int,
    val toStrategyId: Int,
    val quantity: BigDecimal,
    val date: LocalDate,
)
