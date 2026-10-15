package dev.agner.portfolio.usecase.quotes

import dev.agner.portfolio.usecase.catalog.gateway.ICotahistSource
import dev.agner.portfolio.usecase.catalog.model.CotahistRow
import dev.agner.portfolio.usecase.quotes.gateway.ICurrentQuoteSource
import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import dev.agner.portfolio.usecase.quotes.model.TheirCurrentQuote
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
    val currentQuoteSource = mockk<ICurrentQuoteSource>(relaxed = true)
    val service = QuoteService(repository, cotahistSource, currentQuoteSource)

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

    "refreshCurrent should store the brapi quote and return the latest stored row" {
        coEvery { currentQuoteSource.fetchCurrentQuote("PETR4") } returns
            TheirCurrentQuote("PETR4", LocalDate.parse("2026-10-15"), BigDecimal("49.11"))
        val latest = Quote("PETR4", LocalDate.parse("2026-10-15"), BigDecimal("49.11"), QuoteSource.BRAPI)
        coEvery { repository.fetchLatestBy("PETR4") } returns latest
        val captured = slot<List<Quote>>()
        coEvery { repository.upsertAll(capture(captured)) } returns 1

        service.refreshCurrent("petr4f") shouldBe latest

        captured.captured.single() shouldBe Quote(
            ticker = "PETR4",
            date = LocalDate.parse("2026-10-15"),
            close = BigDecimal("49.11"),
            source = QuoteSource.BRAPI,
        )
    }

    "refreshCurrent should fall back to the last stored close when brapi has nothing" {
        coEvery { currentQuoteSource.fetchCurrentQuote("PETR4") } returns null
        val stored = Quote("PETR4", LocalDate.parse("2026-10-14"), BigDecimal("48.92"), QuoteSource.COTAHIST)
        coEvery { repository.fetchLatestBy("PETR4") } returns stored

        service.refreshCurrent("PETR4") shouldBe stored

        coVerify(exactly = 0) { repository.upsertAll(any()) }
    }
})
