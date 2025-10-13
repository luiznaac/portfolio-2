package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.checkingaccount.CheckingAccountService
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountCreation
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountMovementCreation
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
class CheckingAccountController(
    private val service: CheckingAccountService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/checking-accounts") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            post {
                val payload = call.receive<CheckingAccountCreation>()

                call.respond(
                    HttpStatusCode.Created,
                    service.create(payload),
                )
            }

//            post("/{checking_account_id}/consolidate") {
//                val checkingAccountId = call.parameters["checking_account_id"]!!.toInt()
//
//                consolidationOrchestrator.consolidateCheckingAccountBy(checkingAccountId)
//                call.respond(HttpStatusCode.NoContent)
//            }

            post("/{checking_account_id}/deposit") {
                val checkingAccountId = call.parameters["checking_account_id"]!!.toInt()
                val payload = call.receive<MovementRequest>()

                call.respond(
                    HttpStatusCode.Created,
                    service.deposit(payload.toCreation(checkingAccountId)),
                )
            }

            post("/{checking_account_id}/withdraw") {
                val checkingAccountId = call.parameters["checking_account_id"]!!.toInt()
                val payload = call.receive<MovementRequest>()

                call.respond(
                    HttpStatusCode.Created,
                    service.withdraw(payload.toCreation(checkingAccountId)),
                )
            }

            post("/{checking_account_id}/full-withdraw") {
                val checkingAccountId = call.parameters["checking_account_id"]!!.toInt()
                val payload = call.receive<MovementRequest>()

                call.respond(
                    HttpStatusCode.Created,
                    service.fullWithdraw(payload.toCreation(checkingAccountId)),
                )
            }
        }
    }
}

private data class MovementRequest(
    val date: LocalDate,
    val amount: BigDecimal? = null,
) {
    fun toCreation(checkingAccountId: Int) = CheckingAccountMovementCreation(checkingAccountId, date, amount)
}
