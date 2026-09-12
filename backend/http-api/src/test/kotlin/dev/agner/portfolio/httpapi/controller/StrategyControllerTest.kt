package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.configuration.DefaultDomainExceptionStatusMapper
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.strategy.StrategyEditionAlreadyExistsException
import dev.agner.portfolio.usecase.strategy.StrategyEditionService
import dev.agner.portfolio.usecase.strategy.StrategyNotFoundException
import dev.agner.portfolio.usecase.strategy.StrategyService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.serialization.jackson.JacksonConverter
import io.ktor.server.application.Application
import io.ktor.server.application.install
import io.ktor.server.plugins.contentnegotiation.ContentNegotiation
import io.ktor.server.plugins.statuspages.StatusPages
import io.ktor.server.plugins.statuspages.exception
import io.ktor.server.response.respond
import io.ktor.server.routing.get
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk

class StrategyControllerTest : DescribeSpec({

    describe("strategy id path parameter") {

        it("returns 400 with the ApiError payload for a non-numeric id on editions") {
            val service = mockk<StrategyService>(relaxed = true)
            val editionService = mockk<StrategyEditionService>(relaxed = true)

            testApplication {
                application { installController(StrategyController(service, editionService)) }

                val response = client.get("/strategies/abc/editions")
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.BadRequest
                payload.get("error").asText() shouldBe "invalid-strategy-id"
                payload.get("detail").asText() shouldContain "abc"
                coVerify(exactly = 0) { editionService.fetchEditions(any()) }
            }
        }

        it("returns 400 with the ApiError payload for a non-numeric id on reports") {
            val service = mockk<StrategyService>(relaxed = true)
            val editionService = mockk<StrategyEditionService>(relaxed = true)

            testApplication {
                application { installController(StrategyController(service, editionService)) }

                val response = client.post("/strategies/abc/reports")
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.BadRequest
                payload.get("error").asText() shouldBe "invalid-strategy-id"
                payload.get("detail").asText() shouldContain "abc"
                coVerify(exactly = 0) { editionService.importReport(any(), any()) }
            }
        }

        it("returns 400 when the strategy id is missing from the call parameters") {
            val service = mockk<StrategyService>(relaxed = true)
            val editionService = mockk<StrategyEditionService>(relaxed = true)

            testApplication {
                application { installController(StrategyController(service, editionService)) }

                // The real route never matches without an id segment (Ktor answers 404), so this
                // exercises the helper's null branch through the same StatusPages mapping.
                val response = client.get("/test/missing-strategy-id")
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.BadRequest
                payload.get("error").asText() shouldBe "invalid-strategy-id"
                payload.get("detail").asText() shouldContain "<missing>"
                coVerify(exactly = 0) { editionService.fetchEditions(any()) }
            }
        }
    }

    describe("fetching editions") {

        it("returns 404 when the strategy does not exist") {
            val service = mockk<StrategyService>(relaxed = true)
            val editionService = mockk<StrategyEditionService>()
            coEvery { editionService.fetchEditions(99) } throws StrategyNotFoundException(99)

            testApplication {
                application { installController(StrategyController(service, editionService)) }

                val response = client.get("/strategies/99/editions")
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.NotFound
                payload.get("error").asText() shouldBe "strategy-not-found"
            }
        }

        it("returns 200 with an empty list when the strategy has no editions") {
            val service = mockk<StrategyService>(relaxed = true)
            val editionService = mockk<StrategyEditionService>(relaxed = true)
            coEvery { editionService.fetchEditions(5) } returns emptyList()

            testApplication {
                application { installController(StrategyController(service, editionService)) }

                val response = client.get("/strategies/5/editions")

                response.status shouldBe HttpStatusCode.OK
                response.bodyAsText() shouldBe "[]"
            }
        }
    }

    describe("importing a report") {

        it("returns 409 when the reference date was already imported") {
            val service = mockk<StrategyService>(relaxed = true)
            val editionService = mockk<StrategyEditionService>()
            coEvery { editionService.importReport(5, any()) } throws
                StrategyEditionAlreadyExistsException(5, "2026-09-01")

            testApplication {
                application { installController(StrategyController(service, editionService)) }

                val response = client.post("/strategies/5/reports") {
                    setBody(byteArrayOf(1, 2, 3))
                }
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.Conflict
                payload.get("error").asText() shouldBe "strategy-edition-duplicate"
            }
        }
    }
})

private data class TestApiError(val error: String, val message: String, val detail: String)

private fun Application.installController(controller: StrategyController) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(ObjectMapper()))
    }

    install(StatusPages) {
        exception<DomainException> { call, cause ->
            call.respond(
                DefaultDomainExceptionStatusMapper().statusFor(cause),
                TestApiError(error = cause.error, message = cause.userMessage, detail = cause.detail),
            )
        }
    }

    routing(controller.routes())

    routing {
        get("/test/missing-strategy-id") {
            call.respond(call.strategyId())
        }
    }
}
