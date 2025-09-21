package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.index.IndexService
import io.ktor.http.HttpStatusCode
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class IndexController(
    private val indexService: IndexService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/indexes") {
            get {
                call.respond(HttpStatusCode.OK, indexService.fetchAllIndexes())
            }
        }
    }
}
