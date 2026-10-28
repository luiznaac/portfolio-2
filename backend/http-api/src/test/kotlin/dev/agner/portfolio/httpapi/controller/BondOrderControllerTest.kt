package dev.agner.portfolio.httpapi.controller

import dev.agner.portfolio.usecase.bond.BondOrderService
import dev.agner.portfolio.usecase.bond.model.Bond
import dev.agner.portfolio.usecase.bond.model.BondOrder.Contribution.Buy
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import io.ktor.server.testing.testApplication
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class BondOrderControllerTest : DescribeSpec({

    describe("creating a bond order") {

        it("creates it under /fixed-income/bonds/orders") {
            val service = mockk<BondOrderService>()
            coEvery { service.create(any()) } returns buy

            testApplication {
                application { installRoutes(BondOrderController(service)) }

                val response = client.post("/fixed-income/bonds/orders") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"bond_id":1,"type":"BUY","date":"2025-05-30","amount":1000.00}""")
                }

                response.status shouldBe HttpStatusCode.Created
                coVerify(exactly = 1) { service.create(any()) }
            }
        }

        it("no longer serves the old /bonds/orders path") {
            val service = mockk<BondOrderService>()

            testApplication {
                application { installRoutes(BondOrderController(service)) }

                val response = client.post("/bonds/orders") {
                    contentType(ContentType.Application.Json)
                    setBody("""{"bond_id":1,"type":"BUY","date":"2025-05-30","amount":1000.00}""")
                }

                response.status shouldBe HttpStatusCode.NotFound
            }
        }
    }
})

private val prefixado = Bond.FixedRateBond(
    id = 1,
    name = "Tesouro Prefixado 2031",
    rate = BigDecimal("12.50"),
    maturityDate = LocalDate.parse("2031-01-01"),
)

private val buy = Buy(
    id = 1,
    date = LocalDate.parse("2025-05-30"),
    amount = BigDecimal("1000.00"),
    bond = prefixado,
)
