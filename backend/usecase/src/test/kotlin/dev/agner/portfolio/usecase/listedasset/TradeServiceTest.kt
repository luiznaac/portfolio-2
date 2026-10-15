package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeCreation
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class TradeServiceTest : StringSpec({
    val repository = mockk<ITradeRepository>(relaxed = true)
    val service = TradeService(repository)

    beforeTest { clearAllMocks() }

    "create should normalize the ticker before saving" {
        coEvery { repository.save(any()) } returns savedBuy

        service.create(creation(ticker = "alup11f"))

        coVerify(exactly = 1) { repository.save(creation(ticker = "ALUP11")) }
    }

    "create should return the saved trade" {
        coEvery { repository.save(any()) } returns savedBuy

        service.create(creation()) shouldBe savedBuy
    }

    "fetchAllBy should look the ticker up under its normalized key" {
        coEvery { repository.fetchAllBy(any()) } returns emptyList()

        service.fetchAllBy("petr4f")

        coVerify(exactly = 1) { repository.fetchAllBy("PETR4") }
    }
})

private fun creation(ticker: String = "PETR4") = TradeCreation(
    ticker = ticker,
    side = TradeSide.BUY,
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionId = 1,
)

private val savedBuy = Trade.Buy(
    id = 7,
    ticker = "ALUP11",
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = BigDecimal("100"),
    unitPrice = BigDecimal("48.92"),
    institutionId = 1,
    fee = null,
)
