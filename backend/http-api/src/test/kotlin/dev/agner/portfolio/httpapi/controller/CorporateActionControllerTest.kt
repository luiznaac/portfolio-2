package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.import.MovementsParserResolver
import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.import.MovementsImportService
import dev.agner.portfolio.usecase.import.UnsupportedImportFileException
import dev.agner.portfolio.usecase.import.model.MovementsImportResult
import dev.agner.portfolio.usecase.import.model.MovementsRouting
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

class CorporateActionControllerTest : DescribeSpec({

    describe("listing corporate actions") {

        it("returns 200 with the log, review rows included") {
            val service = mockk<CorporateActionService>()
            coEvery { service.fetchAll() } returns listOf(action, reviewAction)

            testApplication {
                application { installRoutes(controller(service)) }

                val response = client.get("/corporate-actions")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.size() shouldBe 2
                body.get(0).get("kind").asText() shouldBe "SPLIT"
                body.get(1).get("status").asText() shouldBe "NEEDS_REVIEW"
            }
        }
    }

    describe("importing movimentações") {

        it("returns 201 with the routing counts and the corporate-action rows") {
            val service = mockk<CorporateActionService>()
            val movements = mockk<MovementsImportService>()
            val parser = mockk<MovementsParserResolver>()
            every { parser.parse("movimentacao.xlsx", any()) } returns MovementsRouting()
            coEvery { movements.import("movimentacao.xlsx", any(), any()) } returns
                MovementsImportResult(trades = 1, incomes = 1, corporateActions = listOf(action))

            testApplication {
                application { installRoutes(controller(service, movements, parser)) }

                val response = client.post("/corporate-actions/import") { setBody(upload()) }

                response.status shouldBe HttpStatusCode.Created
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("trades").asInt() shouldBe 1
                body.get("corporate_actions").get(0).get("ticker").asText() shouldBe "PETR4"
            }
        }

        it("returns 400 with the canonical error body when the file is not a movimentação export") {
            val service = mockk<CorporateActionService>()
            val movements = mockk<MovementsImportService>()
            val parser = mockk<MovementsParserResolver>()
            every { parser.parse("unknown.xlsx", any()) } throws UnsupportedImportFileException("unknown.xlsx")

            testApplication {
                application { installRoutes(controller(service, movements, parser)) }

                val response = client.post("/corporate-actions/import") { setBody(upload("unknown.xlsx")) }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "unsupported-import-file"
            }
        }
    }
})

private fun controller(
    service: CorporateActionService,
    movements: MovementsImportService = mockk(),
    parser: MovementsParserResolver = mockk(),
) = CorporateActionController(service, movements, parser)

private fun upload(fileName: String = "movimentacao.xlsx") = MultiPartFormDataContent(
    formData {
        append(
            "file",
            "abc".toByteArray(),
            Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"") },
        )
    },
)

private val action = CorporateAction(
    id = 1,
    ticker = "PETR4",
    kind = CorporateActionKind.SPLIT,
    exDate = LocalDate.parse("2026-10-20"),
    approvalDate = null,
    ratio = BigDecimal("2"),
    declaredValuePerShare = null,
    targetTicker = null,
    source = CorporateActionSource.B3_MOVEMENTS,
    processNumber = "12345",
    rawType = "Desdobramento",
    ordinal = 1,
    status = CorporateActionStatus.APPLIED,
)

private val reviewAction = action.copy(
    id = 2,
    kind = CorporateActionKind.RIGHTS_ISSUE,
    status = CorporateActionStatus.NEEDS_REVIEW,
)
