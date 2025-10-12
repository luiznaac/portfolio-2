package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.checkingaccount.CheckingAccountService
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccountCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

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
        }
    }
}
