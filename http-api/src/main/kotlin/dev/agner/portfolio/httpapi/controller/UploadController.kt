package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.upload.UploadService
import dev.agner.portfolio.usecase.upload.model.KinvoOrder
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
        route("/upload/kinvo") {
            post("/bond/{bond_id}") {
                val bondId = call.parameters["bond_id"]!!.toInt()
                val analise = call.receive<List<KinvoOrder>>()

                service.createOrders(bondId, analise)

                call.respond(HttpStatusCode.OK, analise)
            }
        }
    }
}
