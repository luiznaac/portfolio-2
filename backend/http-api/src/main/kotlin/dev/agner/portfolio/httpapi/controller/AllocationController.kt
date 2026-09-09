package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.allocation.AllocationService
import dev.agner.portfolio.usecase.allocation.classification.model.ProductClassification
import dev.agner.portfolio.usecase.allocation.model.AssetClassTargetCreation
import dev.agner.portfolio.usecase.allocation.model.CapitalSnapshotCreation
import dev.agner.portfolio.usecase.allocation.model.FixedIncomeSubClassTargetCreation
import io.ktor.http.HttpStatusCode
import io.ktor.server.request.receive
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.post
import io.ktor.server.routing.route
import org.springframework.stereotype.Component

@Component
class AllocationController(
    private val service: AllocationService,
) : ControllerTemplate {

    override fun routes(): RouteDefinition = {
        route("/allocation") {
            get("/plan") {
                call.respond(HttpStatusCode.OK, service.currentPlan())
            }

            get("/capital-snapshots") {
                call.respond(HttpStatusCode.OK, service.fetchCapitalHistory())
            }

            post("/capital-snapshots") {
                val payload = call.receive<CapitalSnapshotCreation>()

                call.respond(HttpStatusCode.Created, service.recordCapitalSnapshot(payload))
            }

            get("/class-targets") {
                call.respond(HttpStatusCode.OK, service.fetchClassTargetHistory())
            }

            post("/class-targets") {
                val payload = call.receive<AssetClassTargetCreation>()

                call.respond(HttpStatusCode.Created, service.setClassTarget(payload))
            }

            get("/fixed-income-subclass-targets") {
                call.respond(HttpStatusCode.OK, service.fetchFixedIncomeSubClassTargetHistory())
            }

            post("/fixed-income-subclass-targets") {
                val payload = call.receive<FixedIncomeSubClassTargetCreation>()

                call.respond(HttpStatusCode.Created, service.setFixedIncomeSubClassTarget(payload))
            }

            get("/classifications") {
                call.respond(HttpStatusCode.OK, service.fetchClassifications())
            }

            post("/classifications") {
                val payload = call.receive<ProductClassification>()

                call.respond(HttpStatusCode.OK, service.classify(payload))
            }
        }
    }
}
