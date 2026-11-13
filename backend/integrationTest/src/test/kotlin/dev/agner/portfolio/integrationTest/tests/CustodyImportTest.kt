package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.getRequest
import dev.agner.portfolio.integrationTest.config.postRequest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.ImportDestination
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

// A custody snapshot applied through the import pipeline must leave the book in the state the
// assistant promised: positions overwritten to the export's quantities, and each delta the ledger
// cannot explain opened as a GAP in Ativos · Lacunas. The gap read and the position read are the
// evidence, so only a MySQL round-trip can prove the transaction wrote both.
@IntegrationTest
class CustodyImportTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-11-13T10:00:00Z")
    }

    "applying a custody snapshot overwrites positions and opens a gap per divergence" {
        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        postRequest<Map<String, Any>> {
            path = "/trades"
            body = tradeBody("PETR4", "100", "10", institution.id)
        }
        postRequest<Map<String, Any>> {
            path = "/trades"
            body = tradeBody("VALE3", "50", "20", institution.id)
        }

        val import = getBean<ImportService>().stage(
            ImportSource.B3_POSITION,
            "posicao.xlsx",
            "custody-snapshot-hash",
            listOf(custodyRow("PETR4", "150"), custodyRow("VALE3", "20")),
            ImportDestination(ImportDestinationObject.POSITIONS),
        ).record
        getBean<ImportService>().review(import.id)
        getBean<ImportService>().apply(import.id)!!.applied shouldBe 2

        val positions = getRequest<List<Map<String, Any>>> { path = "/positions" }
            .associateBy { it["ticker"] }
        (positions.getValue("PETR4")["quantity"] as Number).toInt() shouldBe 150
        (positions.getValue("VALE3")["quantity"] as Number).toInt() shouldBe 20

        val gaps = getRequest<List<Map<String, Any>>> { path = "/gaps" }.associateBy { it["ticker"] }
        (gaps.getValue("PETR4")["quantity"] as Number).toInt() shouldBe 50
        gaps.getValue("PETR4")["realized_pending"] shouldBe null
        (gaps.getValue("VALE3")["quantity"] as Number).toInt() shouldBe 30
        gaps.getValue("VALE3")["realized_pending"] shouldBe true
    }

    "a custody row with an error is rejected without blocking the snapshot" {
        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        postRequest<Map<String, Any>> {
            path = "/trades"
            body = tradeBody("PETR4", "100", "10", institution.id)
        }

        val import = getBean<ImportService>().stage(
            ImportSource.B3_POSITION,
            "posicao.xlsx",
            "custody-error-hash",
            listOf(custodyRow("PETR4", "150"), custodyRow("VALE3", "20", error = "quantity missing")),
            ImportDestination(ImportDestinationObject.POSITIONS),
        ).record
        getBean<ImportService>().review(import.id)
        val result = getBean<ImportService>().apply(import.id)!!

        result.applied shouldBe 1
        result.rejected shouldBe 1
        getRequest<List<Map<String, Any>>> { path = "/positions" }
            .single()["ticker"] shouldBe "PETR4"
    }
})

private fun custodyRow(ticker: String, quantity: String, error: String? = null) = ImportRowPayload(
    date = LocalDate.parse("2026-11-13"),
    side = TradeSide.BUY,
    venue = "CUSTODIA",
    ticker = ticker,
    quantity = BigDecimal(quantity),
    unitPrice = BigDecimal.ZERO,
    institutionName = "CUSTODIA",
    error = error,
)

private fun tradeBody(ticker: String, quantity: String, unitPrice: String, institutionId: Int) = mapOf(
    "ticker" to ticker,
    "side" to "BUY",
    "date" to "2026-11-13",
    "venue" to "VISTA",
    "quantity" to BigDecimal(quantity),
    "unit_price" to BigDecimal(unitPrice),
    "institution_id" to institutionId,
)
