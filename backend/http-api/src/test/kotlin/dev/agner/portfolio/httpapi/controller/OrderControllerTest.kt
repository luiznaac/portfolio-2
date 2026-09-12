package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.httpapi.configuration.DefaultDomainExceptionStatusMapper
import dev.agner.portfolio.usecase.attribution.AttributionService
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.strategy.StrategyNotFoundException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class OrderControllerTest : DescribeSpec({

    describe("applying a transfer suggestion") {

        it("delegates to transferBetweenStrategies and returns 204") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            val attributionService = mockk<AttributionService>(relaxed = true)

            testApplication {
                application { installController(OrderController(planService, attributionService)) }

                val response = client.post("/orders/transfers/apply") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "listed_asset_id": 10,
                          "from_strategy_id": 1,
                          "to_strategy_id": 2,
                          "quantity": 50,
                          "date": "2026-09-15"
                        }
                        """.trimIndent(),
                    )
                }

                response.status shouldBe HttpStatusCode.NoContent
                coVerify(exactly = 1) {
                    attributionService.transferBetweenStrategies(
                        listedAssetId = 10,
                        fromStrategyId = 1,
                        toStrategyId = 2,
                        quantity = BigDecimal("50"),
                        date = LocalDate.parse("2026-09-15"),
                    )
                }
            }
        }

        it("returns 404 when the to-strategy does not exist") {
            val planService = mockk<OrderPlanService>(relaxed = true)
            val attributionService = mockk<AttributionService>(relaxed = true)
            coEvery {
                attributionService.transferBetweenStrategies(
                    listedAssetId = any(),
                    fromStrategyId = any(),
                    toStrategyId = 99,
                    quantity = any(),
                    date = any(),
                )
            } throws StrategyNotFoundException(99)

            testApplication {
                application { installController(OrderController(planService, attributionService)) }

                val response = client.post("/orders/transfers/apply") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {
                          "listed_asset_id": 10,
                          "from_strategy_id": 1,
                          "to_strategy_id": 99,
                          "quantity": 50,
                          "date": "2026-09-15"
                        }
                        """.trimIndent(),
                    )
                }
                val payload = com.fasterxml.jackson.databind.ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.NotFound
                payload.get("error").asText() shouldBe "strategy-not-found"
                payload.get("detail").asText() shouldContain "99"
            }
        }
    }
})

private data class OrderTestApiError(val error: String, val message: String, val detail: String)

private fun Application.installController(controller: OrderController) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
    }

    install(StatusPages) {
        exception<DomainException> { call, cause ->
            call.respond(
                DefaultDomainExceptionStatusMapper().statusFor(cause),
                OrderTestApiError(error = cause.error, message = cause.userMessage, detail = cause.detail),
            )
        }
    }

    routing(controller.routes())
}
