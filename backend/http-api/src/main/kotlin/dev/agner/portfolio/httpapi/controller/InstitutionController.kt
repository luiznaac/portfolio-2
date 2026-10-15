package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.delete
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.put
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class InstitutionController(
    private val service: InstitutionService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/institutions") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            post {
                val payload = call.receive<InstitutionCreation>()
                call.respond(HttpStatusCode.Created, service.create(payload))
            }

            put("/{institution_id}") {
                val id = call.requiredInt("institution_id")
                val payload = call.receive<InstitutionCreation>()
                val institution = service.rename(id, payload.name)
                when (institution) {
                    null -> call.respond(HttpStatusCode.NotFound)
                    else -> call.respond(HttpStatusCode.OK, institution)
                }
            }

            delete("/{institution_id}") {
                service.delete(call.requiredInt("institution_id"))
                call.respond(HttpStatusCode.NoContent)
            }
        }
    }
}
