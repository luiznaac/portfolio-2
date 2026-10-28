package dev.agner.portfolio.usecase.history

import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.GapService
import dev.agner.portfolio.usecase.listedasset.PositionService
import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapCreation
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import dev.agner.portfolio.usecase.quotes.QuoteService
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal

class BackfillGapTest : StringSpec({

    "an upward custody delta becomes a buy GAP at the current close" {
        val saved = slot<GapCreation>()
        val gaps = mockk<IGapRepository>(relaxed = true)
        coEvery { gaps.save(capture(saved)) } answers { saved.captured.toGap() }
        val service = service(
            ledger = listOf(Position("PETR4", BigDecimal("100"), BigDecimal("10"))),
            gaps = gaps,
            close = BigDecimal("12"),
        )

        val report = service.backfill(listOf(CustodyPosition("PETR4", BigDecimal("150"))))

        report.gapsCreated shouldBe 1
        saved.captured.ticker shouldBe "PETR4"
        val buy = saved.captured as GapCreation.MissingBuy
        buy.quantity.compareTo(BigDecimal("50")) shouldBe 0
        buy.unitPrice.compareTo(BigDecimal("12")) shouldBe 0
    }

    "a downward custody delta becomes an unpriced sell GAP" {
        val saved = slot<GapCreation>()
        val gaps = mockk<IGapRepository>(relaxed = true)
        coEvery { gaps.save(capture(saved)) } answers { saved.captured.toGap() }
        val service = service(
            ledger = listOf(Position("PETR4", BigDecimal("100"), BigDecimal("10"))),
            gaps = gaps,
        )

        service.backfill(listOf(CustodyPosition("PETR4", BigDecimal("60"))))

        val sale = saved.captured as GapCreation.MissingSale
        sale.quantity.compareTo(BigDecimal("40")) shouldBe 0
        saved.captured shouldBe GapCreation.MissingSale("PETR4", sale.date, sale.quantity)
    }
})

private fun GapCreation.toGap(): Gap = when (this) {
    is GapCreation.MissingBuy -> Gap.Buy(1, ticker, date, quantity, unitPrice, GapStatus.OPEN)
    is GapCreation.MissingSale -> Gap.Sell(1, ticker, date, quantity, null, GapStatus.OPEN)
}

private fun service(
    ledger: List<Position> = emptyList(),
    gaps: IGapRepository = mockk(relaxed = true),
    trades: ITradeRepository = mockk<ITradeRepository>(relaxed = true).also {
        coEvery { it.fetchAllBySource(any()) } returns emptyList<Trade>()
        coEvery { it.fetchAllBy(any()) } returns emptyList<Trade>()
        coEvery { it.fetchAll() } returns emptyList<Trade>()
    },
    close: BigDecimal? = null,
): BackfillService {
    val transaction = mockk<ITransactionTemplate>()
    coEvery { transaction.execute<Any?>(any()) } coAnswers { firstArg<suspend () -> Any?>().invoke() }
    val positions = mockk<PositionService>(relaxed = true)
    coEvery { positions.fetchAll() } returns ledger
    val quotes = mockk<QuoteService>(relaxed = true)
    if (close != null) coEvery { quotes.latestClose(any()) } returns close
    return BackfillService(
        trades = trades,
        positions = positions,
        gaps = gaps,
        gapService = mockk<GapService>(relaxed = true),
        corporateActions = mockk<ICorporateActionRepository>(relaxed = true).also {
            coEvery { it.fetchAll() } returns emptyList<CorporateAction>()
        },
        institutions = mockk(relaxed = true),
        quotes = quotes,
        transaction = transaction,
    )
}
