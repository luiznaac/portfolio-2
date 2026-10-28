package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.bond.BondService
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.consolidation.ConsolidationService
import dev.agner.portfolio.usecase.index.model.IndexId
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class BondControllerTest : DescribeSpec({

    describe("listing bonds") {

        it("serves /fixed-income/bonds and exposes the derived sub-class") {
            val service = mockk<BondService>()
            coEvery { service.fetchAll() } returns linkedSetOf(prefixado, ipcaPlus)

            testApplication {
                application { installRoutes(bondController(service)) }

                val response = client.get("/fixed-income/bonds")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get(0).get("sub_class").asText() shouldBe "FIXED_RATE"
                body.get(1).get("sub_class").asText() shouldBe "INFLATION_LINKED"
            }
        }

        it("no longer serves the old /bonds path") {
            val service = mockk<BondService>()

            testApplication {
                application { installRoutes(bondController(service)) }

                client.get("/bonds").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("creating bonds") {

        it("creates a fixed rate bond under /fixed-income/bonds/fixed") {
            val service = mockk<BondService>()
            coEvery { service.create(any()) } returns prefixado

            testApplication {
                application { installRoutes(bondController(service)) }

                val response = client.post("/fixed-income/bonds/fixed") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """{"name":"Tesouro Prefixado 2031","rate":12.50,"maturity_date":"2031-01-01"}""",
                    )
                }

                response.status shouldBe HttpStatusCode.Created
                coVerify(exactly = 1) { service.create(any()) }
            }
        }

        it("creates a floating rate bond under /fixed-income/bonds/floating") {
            val service = mockk<BondService>()
            coEvery { service.create(any()) } returns ipcaPlus

            testApplication {
                application { installRoutes(bondController(service)) }

                val response = client.post("/fixed-income/bonds/floating") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {"name":"Tesouro IPCA+ 2035","rate":6.00,"maturity_date":"2035-05-15","index_id":"IPCA"}
                        """.trimIndent(),
                    )
                }

                response.status shouldBe HttpStatusCode.Created
                coVerify(exactly = 1) { service.create(any()) }
            }
        }
    }
})

private fun bondController(service: BondService) = BondController(
    service = service,
    consolidationService = mockk<ConsolidationService>(),
    positionService = mockk<BondPositionService>(),
)

private val prefixado = Bond.FixedRateBond(
    id = 1,
    name = "Tesouro Prefixado 2031",
    rate = BigDecimal("12.50"),
    maturityDate = LocalDate.parse("2031-01-01"),
)

private val ipcaPlus = Bond.FloatingRateBond(
    id = 2,
    name = "Tesouro IPCA+ 2035",
    rate = BigDecimal("6.00"),
    maturityDate = LocalDate.parse("2035-05-15"),
    indexId = IndexId.IPCA,
)
