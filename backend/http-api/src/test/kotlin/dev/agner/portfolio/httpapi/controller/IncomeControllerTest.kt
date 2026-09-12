package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.configuration.DefaultDomainExceptionStatusMapper
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.income.parser.IIncomeStatementParser
import dev.agner.portfolio.usecase.income.parser.IncomeStatementParseException
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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
import io.ktor.server.routing.routing
import io.ktor.server.testing.testApplication
import io.mockk.every
import io.mockk.mockk

class IncomeControllerTest : DescribeSpec({

    describe("reconciling an income statement") {

        it("returns 400 with the ApiError payload when the file can't be parsed") {
            val service = mockk<IncomeService>()
            val parser = mockk<IIncomeStatementParser>()
            every { parser.parse(any()) } throws IncomeStatementParseException("Missing expected columns")

            testApplication {
                application { installController(IncomeController(service, parser)) }

                val response = client.post("/income/reconcile") {
                    setBody(byteArrayOf(1, 2, 3))
                }
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.BadRequest
                payload.get("error").asText() shouldBe "income-statement-invalid"
                payload.get("message").asText() shouldBe "The income statement could not be parsed"
                payload.get("detail").asText() shouldBe "Missing expected columns"
            }
        }

        it("does not map an unexpected internal failure to the client-error contract") {
            val service = mockk<IncomeService>()
            val parser = mockk<IIncomeStatementParser>()
            every { parser.parse(any()) } throws IllegalStateException("parser exploded")

            testApplication {
                application { installController(IncomeController(service, parser)) }

                val response = client.post("/income/reconcile") {
                    setBody(byteArrayOf(1, 2, 3))
                }

                // StatusPages only maps DomainException, so an internal failure must stay a 500
                // instead of becoming a 400 with the same shape as a parse failure.
                response.status shouldBe HttpStatusCode.InternalServerError
            }
        }
    }
})

private data class IncomeTestApiError(val error: String, val message: String, val detail: String)

private fun Application.installController(controller: IncomeController) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
    }

    install(StatusPages) {
        exception<DomainException> { call, cause ->
            call.respond(
                DefaultDomainExceptionStatusMapper().statusFor(cause),
                IncomeTestApiError(
                    error = cause.error,
                    message = cause.userMessage,
                    detail = cause.detail,
                ),
            )
        }
    }

    routing(controller.routes())
}
