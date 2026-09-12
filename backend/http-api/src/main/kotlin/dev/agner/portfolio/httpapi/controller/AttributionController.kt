package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.attribution.model.AttributionMovementCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class AttributionController(
    private val service: AttributionService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/listed-assets/{listed_asset_id}/attribution") {
            get {
                val assetId = call.requiredInt("listed_asset_id")

                call.respond(HttpStatusCode.OK, service.summarize(assetId))
            }

            post("/movements") {
                val assetId = call.requiredInt("listed_asset_id")
                val payload = call.receive<AttributionMovementCreation>()

                call.respond(HttpStatusCode.Created, service.recordMovement(payload.copy(listedAssetId = assetId)))
            }
        }
    }
}
