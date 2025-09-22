package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.index.IndexValueService
import dev.agner.portfolio.usecase.index.model.IndexId
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class IndexValueController(
    private val service: IndexValueService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/indexes/{index_id}/values") {
            get {
                val indexId = IndexId.valueOf(call.pathParameters["index_id"]!!.uppercase())
                call.respond(HttpStatusCode.OK, service.fetchAllIndexValuesBy(indexId))
            }

            post("/hydrate") {
                val indexId = IndexId.valueOf(call.pathParameters["index_id"]!!.uppercase())
                call.respond(HttpStatusCode.OK, service.hydrateIndexValues(indexId))
            }
        }
    }
}
