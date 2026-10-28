package dev.agner.portfolio.usecase.quotes

import dev.agner.portfolio.usecase.catalog.gateway.ICotahistSource
import dev.agner.portfolio.usecase.catalog.model.CotahistRow
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
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class QuoteServiceTest : StringSpec({
    val repository = mockk<IQuoteRepository>(relaxed = true)
    val cotahistSource = mockk<ICotahistSource>(relaxed = true)
    val service = QuoteService(repository, cotahistSource)

    beforeTest { clearAllMocks() }

    "fetchAllBy should look the ticker up under its normalized key" {
        coEvery { repository.fetchAllBy(any()) } returns emptyList()

        service.fetchAllBy("petr4f")

        coVerify(exactly = 1) { repository.fetchAllBy("PETR4") }
    }

    "refreshEod should store the COTAHIST closes for the latest published day" {
        coEvery { cotahistSource.fetchTradingDay() } returns listOf(
            CotahistRow("PETR4", "PETROBRAS", "02", null, LocalDate.parse("2026-10-14"), BigDecimal("48.92")),
        )
        val captured = slot<List<Quote>>()
        coEvery { repository.upsertAll(capture(captured)) } returns 1

        service.refreshEod() shouldBe 1

        coVerify(exactly = 1) { cotahistSource.fetchTradingDay() }
        captured.captured.single() shouldBe Quote(
            ticker = "PETR4",
            date = LocalDate.parse("2026-10-14"),
            close = BigDecimal("48.92"),
            source = QuoteSource.COTAHIST,
        )
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
