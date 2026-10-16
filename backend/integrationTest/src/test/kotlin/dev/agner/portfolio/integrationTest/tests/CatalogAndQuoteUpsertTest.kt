package dev.agner.portfolio.integrationTest.tests

import dev.agner.portfolio.integrationTest.config.ClockMock
import dev.agner.portfolio.integrationTest.config.IntegrationTest
import dev.agner.portfolio.integrationTest.helpers.getBean
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.catalog.repository.ICatalogRepository
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.every
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Instant

// Regression guard for the MySQL conflict-key defect: Exposed's MySQL dialect rejects an UPSERT
// that names its conflict columns, so both repositories rely on the table's unique keys
// implicitly. A mocked unit test cannot see this — only a real MySQL round-trip can.
@IntegrationTest
class CatalogAndQuoteUpsertTest : StringSpec({

    beforeTest {
        every { ClockMock.clock.instant() } returns Instant.parse("2026-10-14T10:00:00Z")
    }

    "catalog upsert inserts a new ticker and preserves first_seen across a re-upsert" {
        val repository = getBean<ICatalogRepository>()
        val firstSeen = LocalDate.parse("2026-10-14")

        repository.upsertAll(
            listOf(
                CatalogEntry(
                    ticker = "ALUP11",
                    shortName = "ALUP11",
                    bdiType = "??",
                    isin = null,
                    assetKind = AssetKind.UNKNOWN,
                    assetClass = null,
                    firstSeen = firstSeen,
                    lastSeen = firstSeen,
                ),
            ),
        ) shouldBe 1

        repository.upsertAll(
            listOf(
                CatalogEntry(
                    ticker = "ALUP11",
                    shortName = "ALUPAR",
                    bdiType = "02",
                    isin = null,
                    assetKind = AssetKind.UNIT,
                    assetClass = null,
                    firstSeen = LocalDate.parse("2026-10-20"),
                    lastSeen = LocalDate.parse("2026-10-20"),
                ),
            ),
        ) shouldBe 1

        repository.fetchBy("ALUP11")!!.also {
            it.shortName shouldBe "ALUPAR"
            it.assetKind shouldBe AssetKind.UNIT
            it.firstSeen shouldBe firstSeen
            it.lastSeen shouldBe LocalDate.parse("2026-10-20")
        }
    }

    "quote upsert overwrites the row on the (ticker, date) unique key" {
        val repository = getBean<IQuoteRepository>()
        val date = LocalDate.parse("2026-10-14")

        repository.upsertAll(
            listOf(
                Quote(
                    ticker = "PETR4",
                    date = date,
                    close = BigDecimal("35.10"),
                    source = QuoteSource.COTAHIST,
                ),
            ),
        ) shouldBe 1

        repository.upsertAll(
            listOf(
                Quote(
                    ticker = "PETR4",
                    date = date,
                    close = BigDecimal("36.42"),
                    source = QuoteSource.COTAHIST,
                ),
            ),
        ) shouldBe 1

        repository.fetchAllBy("PETR4").also {
            it.size shouldBe 1
            // close is decimal(18,6), so the read-back carries the column's scale.
            it.single().close shouldBe BigDecimal("36.420000")
        }
    }
})
