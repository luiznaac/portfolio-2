package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.getRequest
import dev.agner.portfolio.integrationTest.config.postRequest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionCreation
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.ImportDestination
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.tickeralias.repository.ITickerAliasRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

// The stored projection only holds if the trade insert, the ledger replay and the HTTP read all
// agree through a real MySQL round-trip — a mocked unit test cannot see the persistence seam.
@IntegrationTest
class PositionProjectionTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "a manual trade projects a position readable through the API" {
        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        postRequest<Map<String, Any>> {
            path = "/trades"
            body = manualBody(institution.id)
        }

        val detail = getRequest<Map<String, Any>> { path = "/positions/PETR4" }

        val position = detail["position"] as Map<*, *>
        position["ticker"] shouldBe "PETR4"
        (position["quantity"] as Number).toInt() shouldBe 100
        (detail["trades"] as List<*>).size shouldBe 1
    }

    "applying imports reprojects the average across several executions" {
        val importService = getBean<ImportService>()
        apply(importService, "jan.xlsx", "hash-1", listOf(row(quantity = "100", unitPrice = "10")))
        apply(importService, "feb.xlsx", "hash-2", listOf(row(date = "2026-11-01", quantity = "100", unitPrice = "20")))

        getBean<IPositionRepository>().fetchBy("PETR4")!!.also {
            it.quantity.compareTo(BigDecimal("200")) shouldBe 0
            it.averagePrice.compareTo(BigDecimal("15")) shouldBe 0
        }
    }

    "reproject replays the ledger into the stored projection" {
        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        postRequest<Map<String, Any>> {
            path = "/trades"
            body = manualBody(institution.id)
        }

        postRequest<List<Map<String, Any>>> { path = "/positions/reproject" }

        getBean<IPositionRepository>().fetchBy("PETR4")!!.quantity.compareTo(BigDecimal("100")) shouldBe 0
    }

    "a rename with a conversion ratio converts the canonical book and stores the alias" {
        apply(getBean<ImportService>(), "jan.xlsx", "hash-1", listOf(row(ticker = "OLD3")))
        getBean<CorporateActionService>().importEvents(
            listOf(
                CorporateActionCreation(
                    ticker = "OLD3",
                    kind = CorporateActionKind.TICKER_CHANGE,
                    exDate = LocalDate.parse("2026-10-20"),
                    ratio = BigDecimal("2"),
                    targetTicker = "NEW3",
                ),
            ),
        )

        val position = getBean<IPositionRepository>().fetchBy("NEW3")!!
        position.quantity.compareTo(BigDecimal("200")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("5")) shouldBe 0
        getBean<IPositionRepository>().fetchBy("OLD3") shouldBe null
        getBean<ITickerAliasRepository>().fetchAll().single().ratio?.compareTo(BigDecimal("2")) shouldBe 0
    }

    "a rename applies even when the target changes the class digit" {
        apply(getBean<ImportService>(), "jan.xlsx", "hash-1", listOf(row(ticker = "BIDI11")))
        getBean<CorporateActionService>().importEvents(
            listOf(
                CorporateActionCreation(
                    ticker = "BIDI11",
                    kind = CorporateActionKind.TICKER_CHANGE,
                    exDate = LocalDate.parse("2026-10-20"),
                    targetTicker = "INBR32",
                ),
            ),
        )

        getBean<IPositionRepository>().fetchBy("INBR32")!!.quantity.compareTo(BigDecimal("100")) shouldBe 0
        getBean<ITickerAliasRepository>().fetchAll().single().canonicalTicker shouldBe "INBR32"
    }
})

private suspend fun apply(
    service: ImportService,
    fileName: String,
    hash: String,
    rows: List<ImportRowPayload>,
) {
    val destination = ImportDestination(ImportDestinationObject.TRADES)
    val import = service.stage(ImportSource.B3_TRADES, fileName, hash, rows, destination).record
    service.review(import.id)
    service.apply(import.id)
}

private fun row(
    date: String = "2026-10-14",
    quantity: String = "100",
    unitPrice: String = "10",
    ticker: String = "PETR4",
) = ImportRowPayload(
    date = LocalDate.parse(date),
    side = TradeSide.BUY,
    venue = "VISTA",
    ticker = ticker,
    quantity = BigDecimal(quantity),
    unitPrice = BigDecimal(unitPrice),
    institutionName = "XP INVESTIMENTOS",
)

private fun manualBody(institutionId: Int) = mapOf(
    "ticker" to "PETR4",
    "side" to "BUY",
    "date" to "2026-10-14",
    "venue" to "VISTA",
    "quantity" to BigDecimal("100"),
    "unit_price" to BigDecimal("10"),
    "institution_id" to institutionId,
)
