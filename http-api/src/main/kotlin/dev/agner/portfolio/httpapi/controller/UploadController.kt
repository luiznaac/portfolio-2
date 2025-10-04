package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.upload.UploadService
import dev.agner.portfolio.usecase.upload.model.UploadOrder
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class UploadController(
    private val service: UploadService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/upload") {
            post("/kinvo/bond/{bond_id}") {
                val bondId = call.parameters["bond_id"]!!.toInt()
                val analise = call.receive<List<UploadOrder>>()

                val result = service.createOrders(bondId, analise)

                call.respond(HttpStatusCode.OK, result)
            }

            post("/picpay/bond/{bond_id}") {
                val bondId = call.parameters["bond_id"]!!.toInt()
                val analise = call.receive<List<UploadOrder>>()

                val result = service.createOrders(bondId, analise)

                call.respond(HttpStatusCode.OK, result)
            }
        }
    }
}
