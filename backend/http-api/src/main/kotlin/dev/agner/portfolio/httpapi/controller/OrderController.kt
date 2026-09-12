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
