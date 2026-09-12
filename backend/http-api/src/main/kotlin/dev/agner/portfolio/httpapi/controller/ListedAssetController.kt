package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.consolidation.ConsolidationService
import dev.agner.portfolio.usecase.consolidation.ProductType.LISTED_ASSET
import dev.agner.portfolio.usecase.listedasset.ListedAssetService
import dev.agner.portfolio.usecase.listedasset.gateway.IDividendGateway
import dev.agner.portfolio.usecase.listedasset.model.ListedAssetCreation
import dev.agner.portfolio.usecase.listedasset.position.ListedAssetPositionService
import dev.agner.portfolio.usecase.trade.TradeService
import dev.agner.portfolio.usecase.trade.model.TradeCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class ListedAssetController(
    private val service: ListedAssetService,
    private val tradeService: TradeService,
    private val positionService: ListedAssetPositionService,
    private val consolidationService: ConsolidationService,
    private val dividendGateway: IDividendGateway,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/listed-assets") {
            get {
                call.respond(HttpStatusCode.OK, service.fetchAll())
            }

            post {
                val payload = call.receive<ListedAssetCreation>()

                call.respond(HttpStatusCode.Created, service.create(payload))
            }

            route("/{listed_asset_id}") {
                get("/trades") {
                    val assetId = call.requiredInt("listed_asset_id")

                    call.respond(HttpStatusCode.OK, tradeService.fetchByAssetId(assetId))
                }

                post("/trades") {
                    val assetId = call.requiredInt("listed_asset_id")
                    val payload = call.receive<TradeCreation>()

                    call.respond(HttpStatusCode.Created, tradeService.create(payload.copy(assetId = assetId)))
                }

                post("/consolidate") {
                    val assetId = call.requiredInt("listed_asset_id")

                    consolidationService.consolidateProduct(assetId, LISTED_ASSET)
                    call.respond(HttpStatusCode.NoContent)
                }

                get("/positions") {
                    val assetId = call.requiredInt("listed_asset_id")

                    call.respond(HttpStatusCode.OK, positionService.getByAssetId(assetId))
                }

                get("/positions/last") {
                    val assetId = call.requiredInt("listed_asset_id")

                    call.respond(HttpStatusCode.OK, positionService.getLastByAssetId(assetId))
                }

                get("/dividends") {
                    val assetId = call.requiredInt("listed_asset_id")
                    val asset = service.fetchById(assetId)

                    call.respond(HttpStatusCode.OK, dividendGateway.getDividends(asset))
                }
            }
        }
    }
}
