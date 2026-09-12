package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.configuration.DefaultDomainExceptionStatusMapper
import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.monthlyclose.MonthlyCloseAlreadyClosedException
import dev.agner.portfolio.usecase.monthlyclose.MonthlyCloseService
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
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
import io.mockk.coEvery
import io.mockk.mockk
import kotlinx.datetime.LocalDate

class MonthlyCloseControllerTest : DescribeSpec({

    describe("closing a month") {

        it("returns 409 when the month is already closed") {
            val service = mockk<MonthlyCloseService>()
            coEvery { service.close() } throws MonthlyCloseAlreadyClosedException(LocalDate(2026, 9, 1))

            testApplication {
                application { installController(MonthlyCloseController(service)) }

                val response = client.post("/monthly-close/close")
                val payload = ObjectMapper().readTree(response.bodyAsText())

                response.status shouldBe HttpStatusCode.Conflict
                payload.get("error").asText() shouldBe "monthly-close-already-closed"
                payload.get("message").asText() shouldBe "This month is already closed"
                payload.get("detail").asText() shouldBe "Month 2026-09-01 is already fechado"
            }
        }
    }
})

private data class MonthlyCloseTestApiError(val error: String, val message: String, val detail: String)

private fun Application.installController(controller: MonthlyCloseController) {
    install(ContentNegotiation) {
        register(ContentType.Application.Json, JacksonConverter(JsonMapper.mapper))
    }

    install(StatusPages) {
        exception<DomainException> { call, cause ->
            call.respond(
                DefaultDomainExceptionStatusMapper().statusFor(cause),
                MonthlyCloseTestApiError(
                    error = cause.error,
                    message = cause.userMessage,
                    detail = cause.detail,
                ),
            )
        }
    }

    routing(controller.routes())
}
