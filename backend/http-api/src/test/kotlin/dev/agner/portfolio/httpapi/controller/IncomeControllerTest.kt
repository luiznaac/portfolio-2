package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.import.B3IncomeParser
import dev.agner.portfolio.usecase.income.IncomeService
import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.income.model.IncomeCreation
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import dev.agner.portfolio.usecase.income.model.TaxNature
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.forms.MultiPartFormDataContent
import io.ktor.client.request.forms.formData
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.Headers
import io.ktor.http.HttpHeaders
import io.ktor.http.HttpStatusCode
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class IncomeControllerTest : DescribeSpec({

    describe("listing income") {

        it("returns 200 with every movement and its derived lifecycle status") {
            val service = mockk<IncomeService>()
            coEvery { service.fetchAll() } returns listOf(income)

            testApplication {
                application { installRoutes(controller(service)) }

                val response = client.get("/income")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText()).get(0)
                body.get("ticker").asText() shouldBe "PETR4"
                body.get("type").asText() shouldBe "DIVIDEND"
                body.get("status").asText() shouldBe "ANNOUNCED"
            }
        }
    }

    describe("listing upcoming income") {

        it("returns 200 with the not-yet-received movements") {
            val service = mockk<IncomeService>()
            coEvery { service.fetchUpcoming() } returns listOf(income)

            testApplication {
                application { installRoutes(controller(service)) }

                val response = client.get("/income/upcoming")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get(0).get("status").asText() shouldBe "ANNOUNCED"
            }
        }
    }

    describe("importing proventos") {

        it("returns 201 with the imported movements") {
            val service = mockk<IncomeService>()
            val parser = mockk<B3IncomeParser>()
            every { parser.shouldExecute("eventos.xlsx", any()) } returns true
            every { parser.parse("eventos.xlsx", any()) } returns listOf(creation)
            coEvery { service.importEvents(any()) } returns listOf(income)

            testApplication {
                application { installRoutes(controller(service, parser)) }

                val response = client.post("/income/import") { setBody(upload()) }

                response.status shouldBe HttpStatusCode.Created
                ObjectMapper().readTree(response.bodyAsText()).get(0).get("ticker").asText() shouldBe "PETR4"
            }
        }

        it("returns 400 with the canonical error body when the file is not a proventos export") {
            val service = mockk<IncomeService>()
            val parser = mockk<B3IncomeParser>()
            every { parser.shouldExecute("unknown.xlsx", any()) } returns false

            testApplication {
                application { installRoutes(controller(service, parser)) }

                val response = client.post("/income/import") { setBody(upload(fileName = "unknown.xlsx")) }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "unsupported-income-file"
            }
        }
    }
})

private fun controller(service: IncomeService, parser: B3IncomeParser = mockk()) =
    IncomeController(service, parser)

private fun upload(fileName: String = "eventos.xlsx") = MultiPartFormDataContent(
    formData {
        append(
            "file",
            "abc".toByteArray(),
            Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"") },
        )
    },
)

private val income = Income(
    id = 1,
    ticker = "PETR4",
    date = LocalDate.parse("2026-11-20"),
    type = IncomeType.DIVIDEND,
    grossAmount = BigDecimal("78.39"),
    retainedAmount = BigDecimal("13.33"),
    netAmount = BigDecimal("65.06"),
    taxNature = TaxNature.INCOME,
    provenance = Provenance.B3,
    status = IncomeStatus.ANNOUNCED,
)

private val creation = IncomeCreation(
    ticker = "PETR4",
    date = LocalDate.parse("2026-11-20"),
    type = IncomeType.DIVIDEND,
    grossAmount = BigDecimal("78.39"),
    netAmount = BigDecimal("65.06"),
    provenance = Provenance.B3,
)
