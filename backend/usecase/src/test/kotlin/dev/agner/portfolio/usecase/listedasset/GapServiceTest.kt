package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.sell
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class GapServiceTest : StringSpec({

    "a new trade of the GAP's shape retires it and replays the position" {
        val gap = sellGap(tradeWatermark = 4)
        val gaps = mockk<IGapRepository>(relaxed = true)
        val trades = mockk<ITradeRepository>(relaxed = true)
        val positions = mockk<PositionService>(relaxed = true)
        coEvery { gaps.fetchOpenBy("PETR4") } returns listOf(gap)
        coEvery { trades.fetchAllBy("PETR4") } returns listOf(sell(id = 5, date = "2026-03-15", quantity = "40"))
        coEvery { gaps.markReplaced(gap.id) } returns gap.copy(status = GapStatus.REPLACED)

        GapService(gaps, trades, mockk(relaxed = true), positions, transaction()).reconcile("PETR4") shouldBe true

        coVerify { gaps.markReplaced(gap.id) }
        coVerify { positions.reproject("PETR4") }
    }

    "a pre-existing same-size trade does not retire the record" {
        val gap = sellGap(tradeWatermark = 4)
        val gaps = mockk<IGapRepository>(relaxed = true)
        val trades = mockk<ITradeRepository>(relaxed = true)
        coEvery { gaps.fetchOpenBy("PETR4") } returns listOf(gap)
        coEvery { trades.fetchAllBy("PETR4") } returns listOf(sell(id = 3, quantity = "40"))

        GapService(gaps, trades, mockk(relaxed = true), mockk(relaxed = true), transaction())
            .reconcile("PETR4") shouldBe false
        coVerify(exactly = 0) { gaps.markReplaced(any()) }
    }

    "a new applied quantity corporate action retires the record" {
        val gap = sellGap(actionWatermark = 2)
        val gaps = mockk<IGapRepository>(relaxed = true)
        val actions = mockk<ICorporateActionRepository>(relaxed = true)
        coEvery { gaps.fetchOpenBy("PETR4") } returns listOf(gap)
        coEvery { actions.fetchByTicker("PETR4") } returns listOf(split(id = 3))
        coEvery { gaps.markReplaced(gap.id) } returns gap.copy(status = GapStatus.REPLACED)

        GapService(gaps, mockk(relaxed = true), actions, mockk(relaxed = true), transaction())
            .reconcile("PETR4") shouldBe true
        coVerify { gaps.markReplaced(gap.id) }
    }

    "an OPENING seed of the same shape does not retire the record" {
        val gap = sellGap()
        val gaps = mockk<IGapRepository>(relaxed = true)
        val trades = mockk<ITradeRepository>(relaxed = true)
        coEvery { gaps.fetchOpenBy("PETR4") } returns listOf(gap)
        coEvery { trades.fetchAllBy("PETR4") } returns listOf(
            buy(id = 5, source = TradeSource.OPENING, quantity = "40"),
        )

        GapService(gaps, trades, mockk(relaxed = true), mockk(relaxed = true), transaction())
            .reconcile("PETR4") shouldBe false
        coVerify(exactly = 0) { gaps.markReplaced(any()) }
    }

    "a sale's proceeds can be priced and replays the position" {
        val gap = sellGap()
        val gaps = mockk<IGapRepository>(relaxed = true)
        coEvery { gaps.price(gap.id, BigDecimal("18")) } returns gap.copy(unitPrice = BigDecimal("18"))
        val positions = mockk<PositionService>(relaxed = true)

        val priced = GapService(gaps, mockk(relaxed = true), mockk(relaxed = true), positions, transaction())
            .price(gap.id, BigDecimal("18")) as Gap.Sell
        priced.realizedPending shouldBe false
        coVerify { positions.reproject("PETR4") }
    }

    "the whole-book pass retires each ticker's matching record" {
        val gaps = mockk<IGapRepository>(relaxed = true)
        coEvery { gaps.fetchAll() } returns listOf(sellGap())
        val trades = mockk<ITradeRepository>(relaxed = true)
        coEvery { trades.fetchAllBy("PETR4") } returns listOf(sell(id = 5, quantity = "40"))
        val positions = mockk<PositionService>(relaxed = true)

        GapService(gaps, trades, mockk(relaxed = true), positions, transaction()).reconcileAll() shouldBe 1
        coVerify { gaps.markReplaced(1) }
    }
})

private fun sellGap(tradeWatermark: Int = 0, actionWatermark: Int = 0) = Gap.Sell(
    id = 1,
    ticker = "PETR4",
    date = LocalDate.parse("2026-03-15"),
    quantity = BigDecimal("40"),
    unitPrice = null,
    status = GapStatus.OPEN,
    tradeWatermark = tradeWatermark,
    actionWatermark = actionWatermark,
)

private fun transaction(): ITransactionTemplate = mockk<ITransactionTemplate>().also {
    coEvery { it.execute<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
}

private fun split(id: Int) = CorporateAction(
    id = id,
    ticker = "PETR4",
    kind = CorporateActionKind.SPLIT,
    exDate = LocalDate.parse("2026-02-20"),
    approvalDate = null,
    ratio = BigDecimal("2"),
    declaredValuePerShare = null,
    targetTicker = null,
    source = CorporateActionSource.B3_MOVEMENTS,
    processNumber = null,
    rawType = null,
    ordinal = 1,
    status = CorporateActionStatus.APPLIED,
)
