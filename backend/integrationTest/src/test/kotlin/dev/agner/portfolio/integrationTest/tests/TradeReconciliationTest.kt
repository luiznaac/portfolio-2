package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.config.postRequest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.import.ImportService
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.import.model.ImportSource
import dev.agner.portfolio.usecase.institution.InstitutionService
import dev.agner.portfolio.usecase.institution.model.InstitutionCreation
import dev.agner.portfolio.usecase.listedasset.DuplicateTradeException
import dev.agner.portfolio.usecase.listedasset.TradeService
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

// The reconciliation rules only hold if the persistence round-trip preserves the new columns: the
// signed-quantity natural tuple, the source enum, the confirmed flag and the ordinal. A mocked unit
// test cannot see that — only a real MySQL round-trip can.
@IntegrationTest
class TradeReconciliationTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "a manual trade matched by an incoming B3 row is confirmed, not duplicated" {
        val institution = getBean<InstitutionService>().create(InstitutionCreation("XP INVESTIMENTOS"))
        postRequest<Map<String, Any>> {
            path = "/trades"
            body = manualBody(institution.id)
        }

        val importService = getBean<ImportService>()
        apply(importService, "neg.xlsx", "hash-1", listOf(row()))

        val trades = getBean<ITradeRepository>().fetchAll()
        trades.size shouldBe 1
        trades.single().source shouldBe TradeSource.MANUAL
        trades.single().confirmedByB3 shouldBe true
    }

    "re-importing an applied row under a new file hash does not duplicate it" {
        val importService = getBean<ImportService>()
        apply(importService, "jan.xlsx", "hash-1", listOf(row()))
        apply(importService, "jan-copy.xlsx", "hash-2", listOf(row()))

        getBean<ITradeRepository>().fetchAll().size shouldBe 1
    }

    "an identical extra execution in a later file increments the ordinal" {
        val importService = getBean<ImportService>()
        apply(importService, "jan.xlsx", "hash-1", listOf(row()))
        apply(importService, "feb.xlsx", "hash-2", listOf(row(), row()))

        val trades = getBean<ITradeRepository>().fetchAll()
        trades.size shouldBe 2
        trades.map { it.ordinal } shouldBe listOf(1, 2)
    }

    "a manual trade that dupes an already-imported B3 row is refused" {
        val importService = getBean<ImportService>()
        apply(importService, "neg.xlsx", "hash-1", listOf(row()))
        val imported = getBean<ITradeRepository>().fetchAll().single()

        shouldThrow<DuplicateTradeException> {
            getBean<TradeService>().createManual(
                TradeCreation(
                    ticker = "PETR4",
                    side = TradeSide.BUY,
                    date = LocalDate.parse("2026-10-14"),
                    venue = "VISTA",
                    quantity = BigDecimal("100"),
                    unitPrice = BigDecimal("48.92"),
                    institutionId = imported.institutionId,
                ),
            )
        }
    }
})

private suspend fun apply(
    service: ImportService,
    fileName: String,
    hash: String,
    rows: List<ImportRowPayload>,
) {
    val import = service.stage(ImportSource.B3_TRADES, fileName, hash, rows).record
    service.review(import.id)
    service.apply(import.id)
}

private fun row() = ImportRowPayload(
    date = LocalDate.parse("2026-10-14"),
    side = TradeSide.BUY,
    venue = "VISTA",
    ticker = "PETR4",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionName = "XP INVESTIMENTOS",
)

private fun manualBody(institutionId: Int) = mapOf(
    "ticker" to "PETR4",
    "side" to "BUY",
    "date" to "2026-10-14",
    "venue" to "VISTA",
    "quantity" to BigDecimal("100"),
    "unit_price" to BigDecimal("48.92"),
    "institution_id" to institutionId,
)
