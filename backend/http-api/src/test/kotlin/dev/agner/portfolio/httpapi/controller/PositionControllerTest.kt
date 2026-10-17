package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.httpapi.import.B3PositionParser
import dev.agner.portfolio.usecase.listedasset.PositionReconciliationService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.PositionDetail
import dev.agner.portfolio.usecase.listedasset.model.PositionReconciliation
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
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

class PositionControllerTest : DescribeSpec({

    describe("reading a position") {

        it("returns 200 with the position and its trades") {
            val service = mockk<PositionService>()
            coEvery { service.fetchDetail("PETR4") } returns PositionDetail(position, listOf(trade))

            testApplication {
                application { installRoutes(controller(service)) }

                val response = client.get("/positions/PETR4")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get("position").get("ticker").asText() shouldBe "PETR4"
                body.get("position").get("quantity").asText() shouldBe "100"
                body.get("position").get("average_price").asText() shouldBe "10"
                body.get("trades").get(0).get("ticker").asText() shouldBe "PETR4"
            }
        }

        it("returns 404 when the ticker has no position") {
            val service = mockk<PositionService>()
            coEvery { service.fetchDetail("PETR4") } returns null

            testApplication {
                application { installRoutes(controller(service)) }

                client.get("/positions/PETR4").status shouldBe HttpStatusCode.NotFound
            }
        }

        it("returns 200 with every position") {
            val service = mockk<PositionService>()
            coEvery { service.fetchAll() } returns listOf(position)

            testApplication {
                application { installRoutes(controller(service)) }

                client.get("/positions").status shouldBe HttpStatusCode.OK
            }
        }
    }

    describe("reprojecting positions") {

        it("returns 200 with the replayed positions") {
            val service = mockk<PositionService>()
            coEvery { service.reprojectAll() } returns listOf(position)

            testApplication {
                application { installRoutes(controller(service)) }

                val response = client.post("/positions/reproject")

                response.status shouldBe HttpStatusCode.OK
                ObjectMapper().readTree(response.bodyAsText()).get(0).get("ticker").asText() shouldBe "PETR4"
            }
        }
    }

    describe("reconciling a custody export") {

        it("returns 200 with the comparison") {
            val service = mockk<PositionService>()
            val reconciliation = mockk<PositionReconciliationService>()
            val parser = mockk<B3PositionParser>()
            every { parser.shouldExecute("posicao.xlsx", any()) } returns true
            every { parser.parse("posicao.xlsx", any()) } returns listOf(CustodyPosition("PETR4", BigDecimal("100")))
            coEvery { reconciliation.reconcile(any()) } returns
                listOf(PositionReconciliation("PETR4", BigDecimal("100"), BigDecimal("90")))

            testApplication {
                application { installRoutes(controller(service, reconciliation, parser)) }

                val response = client.post("/positions/reconcile") { setBody(positionUpload()) }

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText()).get(0)
                body.get("ticker").asText() shouldBe "PETR4"
                body.get("has_drift").asBoolean() shouldBe true
            }
        }

        it("returns 400 with the canonical error body when the file is not a position export") {
            val service = mockk<PositionService>()
            val parser = mockk<B3PositionParser>()
            every { parser.shouldExecute("unknown.xlsx", any()) } returns false

            testApplication {
                application { installRoutes(controller(service, parser = parser)) }

                val response = client.post("/positions/reconcile") {
                    setBody(positionUpload(fileName = "unknown.xlsx"))
                }

                response.status shouldBe HttpStatusCode.BadRequest
                ObjectMapper().readTree(response.bodyAsText()).get("error").asText() shouldBe
                    "unsupported-position-file"
            }
        }
    }
})

private fun controller(
    service: PositionService,
    reconciliation: PositionReconciliationService = mockk(),
    parser: B3PositionParser = mockk(),
) = PositionController(service, reconciliation, parser)

private fun positionUpload(fileName: String = "posicao.xlsx") = MultiPartFormDataContent(
    formData {
        append(
            "file",
            "abc".toByteArray(),
            Headers.build { append(HttpHeaders.ContentDisposition, "filename=\"$fileName\"") },
        )
    },
)

private val position = Position("PETR4", BigDecimal("100"), BigDecimal("10"))

private val trade = Trade.Buy(
    id = 1,
    ticker = "PETR4",
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("10"),
    institutionId = 1,
    fee = null,
    source = TradeSource.B3_IMPORT,
    confirmedByB3 = false,
    ordinal = 1,
)
