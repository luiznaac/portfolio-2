package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation.BonusCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation.ReverseSplitCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation.SplitCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation.TickerChangeCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class CorporateActionController(
    private val service: CorporateActionService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/listed-assets/{listed_asset_id}/corporate-actions") {
            get {
                val assetId = call.requiredInt("listed_asset_id")

                call.respond(HttpStatusCode.OK, service.fetchByAssetId(assetId))
            }

            post("/split") {
                val assetId = call.requiredInt("listed_asset_id")
                val payload = call.receive<SplitCreation>()

                call.respond(HttpStatusCode.Created, service.create(payload.copy(assetId = assetId)))
            }

            post("/reverse-split") {
                val assetId = call.requiredInt("listed_asset_id")
                val payload = call.receive<ReverseSplitCreation>()

                call.respond(HttpStatusCode.Created, service.create(payload.copy(assetId = assetId)))
            }

            post("/bonus") {
                val assetId = call.requiredInt("listed_asset_id")
                val payload = call.receive<BonusCreation>()

                call.respond(HttpStatusCode.Created, service.create(payload.copy(assetId = assetId)))
            }

            post("/ticker-change") {
                val assetId = call.requiredInt("listed_asset_id")
                val payload = call.receive<TickerChangeCreation>()

                call.respond(HttpStatusCode.Created, service.create(payload.copy(assetId = assetId)))
            }
        }
    }
}
