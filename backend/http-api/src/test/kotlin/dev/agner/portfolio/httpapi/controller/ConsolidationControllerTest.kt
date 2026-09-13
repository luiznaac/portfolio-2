package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.configuration.DefaultDomainExceptionStatusMapper
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.consolidation.ConsolidationService
import dev.agner.portfolio.usecase.consolidation.ProductType
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain
import io.ktor.client.request.post
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
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk

class ConsolidationControllerTest : DescribeSpec({

    describe("consolidating a product") {

        it("returns 200 and consolidates the declared product type") {
            val service = mockk<ConsolidationService>()
            coEvery { service.consolidateProduct(any(), any()) } just Runs

            testApplication {
                application { installController(ConsolidationController(service)) }

                val response = client.post("/consolidations/LISTED_ASSET/7")

                response.status shouldBe HttpStatusCode.OK
                coVerify(exactly = 1) { service.consolidateProduct(7, ProductType.LISTED_ASSET) }
            }
        }
    }

    describe("an unknown product type") {

        it("returns 500 and never reaches the service") {
            val service = mockk<ConsolidationService>()

            testApplication {
                application { installController(ConsolidationController(service)) }

                val response = client.post("/consolidations/UNKNOWN/1")

                // ProductType.valueOf throws an IllegalArgumentException that nothing maps yet, so
                // the request dies in the pipeline. This pins today's accidental 500; a later fix
                // should turn it into a 400 with error "invalid-parameter", consistent with the
                // product id handling below.
                response.status shouldBe HttpStatusCode.InternalServerError
                coVerify(exactly = 0) { service.consolidateProduct(any(), any()) }
            }
        }
    }

    describe("a non-numeric product id") {

        it("returns 400 with the ApiError payload and never reaches the service") {
            val service = mockk<ConsolidationService>()

            testApplication {
                application { installController(ConsolidationController(service)) }

                val response = client.post("/consolidations/LISTED_ASSET/abc")
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.BadRequest
                payload.get("error").asText() shouldBe "invalid-parameter"
                payload.get("detail").asText() shouldContain "product_id"
                coVerify(exactly = 0) { service.consolidateProduct(any(), any()) }
            }
        }
    }
})

private data class ConsolidationTestApiError(val error: String, val message: String, val detail: String)

private fun Application.installController(controller: ConsolidationController) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
    }

    install(StatusPages) {
        exception<DomainException> { call, cause ->
            call.respond(
                DefaultDomainExceptionStatusMapper().statusFor(cause),
                ConsolidationTestApiError(
                    error = cause.error,
                    message = cause.userMessage,
                    detail = cause.detail,
                ),
            )
        }
    }

    routing(controller.routes())
}
