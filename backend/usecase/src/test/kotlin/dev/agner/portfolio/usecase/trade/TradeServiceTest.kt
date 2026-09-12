package dev.agner.portfolio.usecase.trade

import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.model.TradeCreation
import dev.agner.portfolio.usecase.trade.model.TradeSide
import dev.agner.portfolio.usecase.trade.repository.ITradeRepository
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class TradeServiceTest : StringSpec({
    val repository = mockk<ITradeRepository>()
    val service = TradeService(repository)

    val date = LocalDate(2026, 9, 1)

    "should reject a negative quantity before it reaches the ledger" {
        val creation = TradeCreation(date, TradeSide.BUY, BigDecimal("-100"), BigDecimal("35.50"))

        shouldThrow<IllegalArgumentException> { service.create(1, creation) }

        coVerify(exactly = 0) { repository.save(any(), any()) }
    }

    "should reject a zero quantity before it reaches the ledger" {
        val creation = TradeCreation(date, TradeSide.SELL, BigDecimal.ZERO, BigDecimal("35.50"))

        shouldThrow<IllegalArgumentException> { service.create(1, creation) }

        coVerify(exactly = 0) { repository.save(any(), any()) }
    }

    "should delegate a positive quantity unchanged" {
        val creation = TradeCreation(date, TradeSide.BUY, BigDecimal("100"), BigDecimal("35.50"))
        coEvery { repository.save(1, creation) } returns mockk<Trade>()

        service.create(1, creation)

        coVerify(exactly = 1) { repository.save(1, creation) }
    }
})
