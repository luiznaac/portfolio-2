package dev.agner.portfolio.httpapi.controller

import com.fasterxml.jackson.databind.ObjectMapper
import dev.agner.portfolio.usecase.bond.position.BondPositionService
import dev.agner.portfolio.usecase.checkingaccount.CheckingAccountService
import dev.agner.portfolio.usecase.checkingaccount.model.CheckingAccount
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
import kotlinx.datetime.DatePeriod
import java.math.BigDecimal

class CheckingAccountControllerTest : DescribeSpec({

    describe("listing checking accounts") {

        it("serves /fixed-income/accounts and exposes the derived sub-class") {
            val service = mockk<CheckingAccountService>()
            coEvery { service.fetchAll() } returns setOf(cdb)

            testApplication {
                application { installRoutes(checkingAccountController(service)) }

                val response = client.get("/fixed-income/accounts")

                response.status shouldBe HttpStatusCode.OK
                val body = ObjectMapper().readTree(response.bodyAsText())
                body.get(0).get("sub_class").asText() shouldBe "FLOATING_RATE"
                body.get(0).get("maturity_duration").asText() shouldBe "P3Y"
            }
        }

        it("no longer serves the old /checking-accounts path") {
            val service = mockk<CheckingAccountService>()

            testApplication {
                application { installRoutes(checkingAccountController(service)) }

                client.get("/checking-accounts").status shouldBe HttpStatusCode.NotFound
            }
        }
    }

    describe("creating a checking account") {

        it("creates it under /fixed-income/accounts") {
            val service = mockk<CheckingAccountService>()
            coEvery { service.create(any()) } returns cdb

            testApplication {
                application { installRoutes(checkingAccountController(service)) }

                val response = client.post("/fixed-income/accounts") {
                    contentType(ContentType.Application.Json)
                    setBody(
                        """
                        {"name":"CDB Banco Master 110% CDI","rate":110.0,"index_id":"CDI","maturity_duration":"P3Y"}
                        """.trimIndent(),
                    )
                }

                response.status shouldBe HttpStatusCode.Created
                coVerify(exactly = 1) { service.create(any()) }
            }
        }
    }
})

private fun checkingAccountController(service: CheckingAccountService) = CheckingAccountController(
    service = service,
    consolidationService = mockk<ConsolidationService>(),
    positionService = mockk<BondPositionService>(),
)

private val cdb = CheckingAccount(
    id = 1,
    name = "CDB Banco Master 110% CDI",
    rate = BigDecimal("110.00"),
    indexId = IndexId.CDI,
    maturityDuration = DatePeriod.parse("P3Y"),
)
