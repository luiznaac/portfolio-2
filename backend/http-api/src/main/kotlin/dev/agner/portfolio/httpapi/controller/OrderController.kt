package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.order.OrderPlanService
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * The HTTP contract behind the command/query split of [OrderPlanService]:
 *
 * - `GET /orders/plan` is a pure read — it returns the stored PENDING transfer proposals and never
 *   creates, refreshes or auto-applies one.
 * - `POST /orders/plan/refresh` is the only route that reconciles the month: it creates newly
 *   matched proposals, updates the quantity on the still-pending ones and auto-applies anything
 *   under the threshold.
 *
 * `MonthlyCloseService.close()` calls `refreshPlan()` on its own, so closing a fresh month can
 * itself create proposals and then block on them with a `PendingTransferProposalsException`. A
 * client that refreshes first gets the proposals to decide on before the close.
 */
@Component
class OrderController(
    private val planService: OrderPlanService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/orders") {
            // GET only reads: transfer proposals come back as stored. Creating newly matched ones,
            // refreshing their quantity and auto-applying the small ones is the refresh command.
            get("/plan") {
                call.respond(HttpStatusCode.OK, planService.computePlan())
            }

            post("/plan/refresh") {
                call.respond(HttpStatusCode.OK, planService.refreshPlan())
            }

            route("/transfers") {
                get {
                    call.respond(HttpStatusCode.OK, planService.transfersForMonth())
                }

                get("/settings") {
                    call.respond(HttpStatusCode.OK, planService.transferSettings())
                }

                put("/settings") {
                    val payload = call.receive<TransferSettingsRequest>()

                    call.respond(HttpStatusCode.OK, planService.setTransferSettings(payload.autoApprovalThreshold))
                }

                route("/{transfer_id}") {
                    post("/approve") {
                        val id = call.requiredInt("transfer_id")
                        val payload = call.receive<ApproveTransferRequest>()

                        call.respond(HttpStatusCode.OK, planService.approveTransfer(id, payload.quantity))
                    }

                    post("/reject") {
                        val id = call.requiredInt("transfer_id")

                        call.respond(HttpStatusCode.OK, planService.rejectTransfer(id))
                    }
                }
            }
        }
    }
}

private data class ApproveTransferRequest(val quantity: BigDecimal? = null)

private data class TransferSettingsRequest(val autoApprovalThreshold: BigDecimal)
