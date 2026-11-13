package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
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
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import java.math.BigDecimal

// The custody snapshot is the import assistant's POSITIONS apply: every delta the ledger cannot
// explain becomes a GAP, and the tickers the gaps explain are replayed to the snapshot. The
// divergence is recorded, never a blocker — reconciliation (pricing the gap) stays a separate act.
class CustodySnapshotServiceTest : StringSpec({

    "an upward custody delta becomes a buy gap at the current close" {
        val saved = slot<GapCreation>()
        val gaps = mockk<IGapRepository>(relaxed = true)
        coEvery { gaps.save(capture(saved)) } answers { saved.captured.toGap() }
        val service = service(
            ledger = listOf(Position("PETR4", BigDecimal("100"), BigDecimal("10"))),
            gaps = gaps,
            close = BigDecimal("12"),
        )

        service.apply(listOf(CustodyPosition("PETR4", BigDecimal("150"))))

        val buy = saved.captured as GapCreation.MissingBuy
        buy.ticker shouldBe "PETR4"
        buy.quantity.compareTo(BigDecimal("50")) shouldBe 0
        buy.unitPrice.compareTo(BigDecimal("12")) shouldBe 0
    }

    "a downward custody delta becomes an unpriced sell gap with pending proceeds" {
        val saved = slot<GapCreation>()
        val gaps = mockk<IGapRepository>(relaxed = true)
        coEvery { gaps.save(capture(saved)) } answers { saved.captured.toGap() }
        val service = service(
            ledger = listOf(Position("PETR4", BigDecimal("100"), BigDecimal("10"))),
            gaps = gaps,
        )

        service.apply(listOf(CustodyPosition("PETR4", BigDecimal("60"))))

        val sale = saved.captured as GapCreation.MissingSale
        sale.quantity.compareTo(BigDecimal("40")) shouldBe 0
        saved.captured shouldBe GapCreation.MissingSale("PETR4", sale.date, sale.quantity)
    }

    "a ledger ticker the snapshot drops becomes a full sell gap" {
        val saved = slot<GapCreation>()
        val gaps = mockk<IGapRepository>(relaxed = true)
        coEvery { gaps.save(capture(saved)) } answers { saved.captured.toGap() }
        val service = service(
            ledger = listOf(Position("VALE3", BigDecimal("30"), BigDecimal("10"))),
            gaps = gaps,
        )

        service.apply(listOf(CustodyPosition("PETR4", BigDecimal("10"))))

        val sale = saved.captured as GapCreation.MissingSale
        sale.ticker shouldBe "VALE3"
        sale.quantity.compareTo(BigDecimal("30")) shouldBe 0
    }

    "an aligned ticker opens no gap" {
        val gaps = mockk<IGapRepository>(relaxed = true)
        val service = service(
            ledger = listOf(Position("PETR4", BigDecimal("100"), BigDecimal("10"))),
            gaps = gaps,
        )

        service.apply(listOf(CustodyPosition("PETR4", BigDecimal("100"))))

        coVerify(exactly = 0) { gaps.save(any()) }
    }

    "apply replays every ticker a gap explains and leaves the aligned ones untouched" {
        val saved = slot<GapCreation>()
        val gaps = mockk<IGapRepository>(relaxed = true)
        coEvery { gaps.save(capture(saved)) } answers { saved.captured.toGap() }
        val positions = mockk<PositionService>(relaxed = true)
        coEvery { positions.fetchAll() } returns listOf(
            Position("PETR4", BigDecimal("100"), BigDecimal("10")),
            Position("VALE3", BigDecimal("50"), BigDecimal("10")),
        )
        val service = service(positions = positions, gaps = gaps)

        service.apply(
            listOf(
                CustodyPosition("PETR4", BigDecimal("150")),
                CustodyPosition("VALE3", BigDecimal("50")),
            ),
        )

        coVerify { positions.reproject("PETR4") }
        coVerify(exactly = 0) { positions.reproject("VALE3") }
    }

    "an unpriceable upward delta opens no gap and leaves the position untouched" {
        val gaps = mockk<IGapRepository>(relaxed = true)
        val positions = mockk<PositionService>(relaxed = true)
        coEvery { positions.fetchAll() } returns emptyList<Position>()
        val service = service(positions = positions, gaps = gaps)

        service.apply(listOf(CustodyPosition("PETR4", BigDecimal("100"))))

        coVerify(exactly = 0) { gaps.save(any()) }
        coVerify(exactly = 0) { positions.reproject(any()) }
    }
})

private fun GapCreation.toGap(): Gap = when (this) {
    is GapCreation.MissingBuy -> Gap.Buy(1, ticker, date, quantity, unitPrice, GapStatus.OPEN)
    is GapCreation.MissingSale -> Gap.Sell(1, ticker, date, quantity, null, GapStatus.OPEN)
}

private fun service(
    ledger: List<Position> = emptyList(),
    positions: PositionService = mockk<PositionService>(relaxed = true).also {
        coEvery { it.fetchAll() } returns ledger
    },
    gaps: IGapRepository = mockk(relaxed = true),
    close: BigDecimal? = null,
): CustodySnapshotService {
    val quotes = mockk<QuoteService>(relaxed = true)
    coEvery { quotes.latestClose(any()) } returns close
    return CustodySnapshotService(
        positions = positions,
        gaps = gaps,
        trades = mockk<ITradeRepository>(relaxed = true).also {
            coEvery { it.fetchAll() } returns emptyList<Trade>()
        },
        corporateActions = mockk<ICorporateActionRepository>(relaxed = true).also {
            coEvery { it.fetchAll() } returns emptyList<CorporateAction>()
        },
        quotes = quotes,
    )
}
