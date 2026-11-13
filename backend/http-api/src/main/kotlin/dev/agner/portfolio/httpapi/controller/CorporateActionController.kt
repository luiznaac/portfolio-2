package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.import.MovementsParserResolver
import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionResolution
import dev.agner.portfolio.usecase.import.MovementsImportService
import dev.agner.portfolio.usecase.import.model.sha256Hex
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

// The corporate-action surface: the log (applied events plus the review queue), the Movimentações
// import that feeds it, and the owner's decision on a parked event. A GET never writes; the import
// and the resolve are their own POST routes, and a file that is not a Movimentação export is
// refused with the canonical 400.
@Component
class CorporateActionController(
    private val service: CorporateActionService,
    private val movements: MovementsImportService,
    private val parsers: MovementsParserResolver,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/corporate-actions") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            post("/import") {
                val upload = call.receiveUpload()
                val routing = parsers.parse(upload.fileName, upload.content)
                call.respond(
                    HttpStatusCode.Created,
                    movements.import(upload.fileName, upload.content.sha256Hex(), routing, upload.content),
                )
            }

            // The review decision: the supplied data fills what the export lacked, or acknowledges a
            // kind the engine cannot compute. The response is the event now in the applied history.
            post("/{id}/resolve") {
                val resolution = call.receive<CorporateActionResolution>()
                call.respond(HttpStatusCode.OK, service.resolve(call.requiredInt("id"), resolution))
            }
        }
    }
}
