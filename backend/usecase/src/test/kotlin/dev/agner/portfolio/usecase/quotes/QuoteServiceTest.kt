package dev.agner.portfolio.usecase.quotes

import dev.agner.portfolio.usecase.cotahistRow
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.repository.IQuoteRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.flowOf
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class QuoteServiceTest : StringSpec({
    val repository = mockk<IQuoteRepository>(relaxed = true)
    val service = QuoteService(repository)

    beforeTest { clearAllMocks() }

    "fetchAllBy should look the ticker up under its normalized key" {
        coEvery { repository.fetchAllBy(any()) } returns emptyList()

        service.fetchAllBy("petr4f")

        coVerify(exactly = 1) { repository.fetchAllBy("PETR4") }
    }

    "storeDailyCloses should store every parsed row as a COTAHIST close" {
        val captured = slot<List<Quote>>()
        coEvery { repository.upsertAll(capture(captured)) } returns 1

        service.storeDailyCloses(
            listOf(
                cotahistRow(
                    ticker = "PETR4",
                    shortName = "PETROBRAS",
                    date = "2026-10-14",
                    closePrice = "48.92",
                ),
            ),
        ) shouldBe 1

        captured.captured.single() shouldBe Quote(
            ticker = "PETR4",
            date = LocalDate.parse("2026-10-14"),
            close = BigDecimal("48.92"),
            source = QuoteSource.COTAHIST,
        )
    }

    "storeMonthlyCloses should keep the last close of each month for the ledger tickers only" {
        val captured = slot<List<Quote>>()
        coEvery { repository.upsertAll(capture(captured)) } returns 2

        service.storeMonthlyCloses(
            flowOf(
                cotahistRow(ticker = "PETR4", date = "2020-01-02", closePrice = "10"),
                cotahistRow(ticker = "PETR4", date = "2020-01-31", closePrice = "11"),
                cotahistRow(ticker = "PETR4", date = "2020-02-28", closePrice = "12"),
                cotahistRow(ticker = "VALE3", date = "2020-01-31", closePrice = "20"),
            ),
            setOf("PETR4"),
        ).let {
            it.count shouldBe 2
            it.latestDate shouldBe LocalDate.parse("2020-02-28")
        }

        captured.captured.map { it.ticker to it.date } shouldBe listOf(
            "PETR4" to LocalDate.parse("2020-01-31"),
            "PETR4" to LocalDate.parse("2020-02-28"),
        )
        captured.captured.map { it.close } shouldBe listOf(BigDecimal("11"), BigDecimal("12"))
    }

    "storeMonthlyCloses is idempotent: a re-run stores the same rows" {
        val stored = mutableListOf<List<Quote>>()
        coEvery { repository.upsertAll(any()) } answers {
            stored += firstArg<List<Quote>>()
            2
        }
        val rows = listOf(
            cotahistRow(ticker = "PETR4", date = "2020-01-30", closePrice = "10"),
            cotahistRow(ticker = "PETR4", date = "2020-01-31", closePrice = "11"),
        )

        service.storeMonthlyCloses(flowOf(*rows.toTypedArray()), setOf("PETR4"))
        service.storeMonthlyCloses(flowOf(*rows.toTypedArray()), setOf("PETR4"))

        stored.size shouldBe 2
        stored[1] shouldBe stored[0]
    }

    "latestClose is null when the ticker has no stored close" {
        coEvery { repository.fetchAllBy("VALE3") } returns emptyList()

        service.latestClose("vale3") shouldBe null
    }

    "latestClose picks the newest stored close from the canonical ticker" {
        coEvery { repository.fetchAllBy("VALE3") } returns listOf(
            quote("2026-10-08", "9"),
            quote("2026-10-12", "12"),
            quote("2026-10-10", "10"),
        )

        service.latestClose("vale3") shouldBe BigDecimal("12")
        coVerify(exactly = 1) { repository.fetchAllBy("VALE3") }
    }
})

private fun quote(date: String, close: String) = Quote(
    ticker = "VALE3",
    date = LocalDate.parse(date),
    close = BigDecimal(close),
    source = QuoteSource.COTAHIST,
)
