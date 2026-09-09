package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import dev.agner.portfolio.usecase.attribution.model.AttributionReason.TRANSFERENCIA
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
            // movements, reason TRANSFERENCIA: it's bookkeeping, not a trade.
            post("/transfers/apply") {
                val payload = call.receive<ApplyTransferRequest>()

                attributionService.recordMovement(
                    AttributionMovementCreation(
                        listedAssetId = payload.listedAssetId,
                        strategyId = payload.fromStrategyId,
                        date = payload.date,
                        quantity = -payload.quantity,
                        reason = TRANSFERENCIA,
                        note = "Transferência para a estratégia ${payload.toStrategyId}",
                    ),
                )
                attributionService.recordMovement(
                    AttributionMovementCreation(
                        listedAssetId = payload.listedAssetId,
                        strategyId = payload.toStrategyId,
                        date = payload.date,
                        quantity = payload.quantity,
                        reason = TRANSFERENCIA,
                        note = "Transferência da estratégia ${payload.fromStrategyId}",
                    ),
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
