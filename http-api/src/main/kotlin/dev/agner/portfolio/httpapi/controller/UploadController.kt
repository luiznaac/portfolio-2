package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.upload.model.KinvoOrder
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class UploadController : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/upload") {
            post("/kinvo") {
                val analise = call.receive<List<KinvoOrder>>()

                call.respond(HttpStatusCode.OK, analise)
            }
        }
    }
}
