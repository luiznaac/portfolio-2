package dev.agner.portfolio.usecase.listedasset

import dev.agner.portfolio.usecase.corporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.repository.ICorporateActionRepository
import dev.agner.portfolio.usecase.listedasset.model.Position
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.listedasset.repository.IGapRepository
import dev.agner.portfolio.usecase.listedasset.repository.IPositionRepository
import dev.agner.portfolio.usecase.listedasset.repository.ITradeRepository
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class PositionServiceTest : StringSpec({
    val positions = mockk<IPositionRepository>(relaxed = true)
    val trades = mockk<ITradeRepository>(relaxed = true)
    val corporateActions = mockk<ICorporateActionRepository>(relaxed = true)
    val service = PositionService(positions, trades, corporateActions, mockk<IGapRepository>(relaxed = true))

    beforeTest { clearAllMocks() }

    "reproject replays the ticker's ledger and persists the result" {
        coEvery { trades.fetchAllBy("PETR4") } returns listOf(buy(quantity = "100", unitPrice = "10"))
        coEvery { corporateActions.fetchByTicker("PETR4") } returns emptyList()
        val saved = slot<Position>()
        coEvery { positions.save(capture(saved)) } returns Unit

        val position = service.reproject("petr4")

        position.ticker shouldBe "PETR4"
        position.quantity.compareTo(BigDecimal("100")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("10")) shouldBe 0
        saved.captured shouldBe position
    }

    "reprojectAll groups the whole ledger by ticker and saves one position each" {
        coEvery { corporateActions.fetchApplied() } returns emptyList()
        coEvery { trades.fetchAll() } returns listOf(
            buy(id = 1, ticker = "PETR4", quantity = "100", unitPrice = "10"),
            buy(id = 2, ticker = "VALE3", quantity = "50", unitPrice = "20"),
            buy(id = 3, ticker = "PETR4", quantity = "100", unitPrice = "20"),
        )

        val reprojected = service.reprojectAll()

        reprojected.map { it.ticker } shouldBe listOf("PETR4", "VALE3")
        reprojected.single { it.ticker == "PETR4" }.averagePrice.compareTo(BigDecimal("15")) shouldBe 0
        coVerify(exactly = 2) { positions.save(any()) }
    }

    "reproject folds in the ticker's applied corporate actions" {
        coEvery { trades.fetchAllBy("PETR4") } returns listOf(buy(quantity = "100", unitPrice = "10"))
        coEvery { corporateActions.fetchByTicker("PETR4") } returns listOf(split())
        val saved = slot<Position>()
        coEvery { positions.save(capture(saved)) } returns Unit

        val position = service.reproject("PETR4")

        position.quantity.compareTo(BigDecimal("200")) shouldBe 0
        position.averagePrice.compareTo(BigDecimal("5")) shouldBe 0
        saved.captured shouldBe position
    }

    "fetchBy looks the ticker up under its normalized key" {
        coEvery { positions.fetchBy("PETR4") } returns storedPosition

        service.fetchBy("petr4f") shouldBe storedPosition

        coVerify(exactly = 1) { positions.fetchBy("PETR4") }
    }

    "fetchDetail returns the stored position with its ledger" {
        coEvery { positions.fetchBy("PETR4") } returns storedPosition
        coEvery { trades.fetchAllBy("PETR4") } returns listOf(buy())

        val detail = service.fetchDetail("petr4")

        detail?.position shouldBe storedPosition
        detail?.trades?.size shouldBe 1
    }

    "fetchDetail returns null when the ticker has no position" {
        coEvery { positions.fetchBy("PETR4") } returns null

        service.fetchDetail("petr4") shouldBe null
    }
})

private val storedPosition = Position("PETR4", BigDecimal("100"), BigDecimal("10"))

private fun split() = corporateAction(CorporateActionKind.SPLIT, ratio = "2")

private fun buy(
    id: Int = 1,
    ticker: String = "PETR4",
    quantity: String = "100",
    unitPrice: String = "10",
) = Trade.Buy(
    id = id,
    ticker = ticker,
    date = LocalDate.parse("2026-10-14"),
    venue = "VISTA",
    quantity = BigDecimal(quantity),
    unitPrice = BigDecimal(unitPrice),
    institutionId = 1,
    fee = null,
    source = TradeSource.B3_IMPORT,
    confirmedByB3 = false,
    ordinal = id,
)
