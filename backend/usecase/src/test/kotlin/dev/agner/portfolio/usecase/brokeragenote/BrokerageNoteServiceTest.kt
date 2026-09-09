package dev.agner.portfolio.usecase.brokeragenote

import dev.agner.portfolio.usecase.brokeragenote.model.ImportedTradeConfirmation
import dev.agner.portfolio.usecase.brokeragenote.parser.BrokerageNoteParseException
import dev.agner.portfolio.usecase.brokeragenote.parser.IBrokerageNoteParser
import dev.agner.portfolio.usecase.brokeragenote.parser.ParsedTrade
import dev.agner.portfolio.usecase.brokeragenote.parser.TradeSide
import dev.agner.portfolio.usecase.listedasset.repository.IListedAssetRepository
import dev.agner.portfolio.usecase.order.OrderPlanService
import dev.agner.portfolio.usecase.order.model.Order
import dev.agner.portfolio.usecase.order.model.OrderKind
import dev.agner.portfolio.usecase.order.model.OrderPlan
import dev.agner.portfolio.usecase.order.model.SaleCeiling
import dev.agner.portfolio.usecase.trade.TradeService
import dev.agner.portfolio.usecase.trade.model.Trade
import dev.agner.portfolio.usecase.trade.model.TradeCreation
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class BrokerageNoteServiceTest : StringSpec({
    val parser = mockk<IBrokerageNoteParser>()
    val listedAssetRepository = mockk<IListedAssetRepository>()
    val tradeService = mockk<TradeService>()
    val orderPlanService = mockk<OrderPlanService>()

    val service = BrokerageNoteService(parser, listedAssetRepository, tradeService, orderPlanService)

    val xlsxBytes = byteArrayOf(1, 2, 3)
    val date = LocalDate(2026, 9, 1)

    "should resolve tickers, sign quantities and flag rows matching the current order plan" {
        every { parser.parse(xlsxBytes) } returns listOf(
            ParsedTrade(date, "PETR4", TradeSide.COMPRA, BigDecimal("100"), BigDecimal("35.50")),
            ParsedTrade(date, "VALE3", TradeSide.VENDA, BigDecimal("10"), BigDecimal("70.00")),
        )
        coEvery { orderPlanService.computePlan() } returns OrderPlan(
            orders = listOf(
                order(ticker = "PETR4", kind = OrderKind.COMPRAR),
                order(ticker = "VALE3", kind = OrderKind.COMPRAR),
            ),
            transferSuggestions = emptyList(),
            saleCeiling = ceiling(),
        )
        coEvery { listedAssetRepository.resolveIdByTicker("PETR4", date) } returns 1
        coEvery { listedAssetRepository.resolveIdByTicker("VALE3", date) } returns 2

        val preview = service.preview(xlsxBytes)

        preview.trades[0].quantity shouldBe BigDecimal("100")
        preview.trades[0].matchesPlan shouldBe true
        preview.trades[1].quantity shouldBe BigDecimal("-10")
        preview.trades[1].matchesPlan shouldBe false
        preview.unresolvedTickers shouldBe emptyList()
    }

    "should flag a ticker that can't be resolved to a registered asset" {
        every { parser.parse(xlsxBytes) } returns listOf(
            ParsedTrade(date, "NOVA11", TradeSide.COMPRA, BigDecimal("5"), BigDecimal("10.00")),
        )
        coEvery { orderPlanService.computePlan() } returns OrderPlan(emptyList(), emptyList(), ceiling())
        coEvery { listedAssetRepository.resolveIdByTicker("NOVA11", date) } returns null

        val preview = service.preview(xlsxBytes)

        preview.trades[0].resolvable shouldBe false
        preview.unresolvedTickers shouldBe listOf("NOVA11")
    }

    "should fail loudly when the statement has no trades" {
        every { parser.parse(xlsxBytes) } returns emptyList()

        shouldThrow<BrokerageNoteParseException> { service.preview(xlsxBytes) }
    }

    "confirm should create one trade per approved row" {
        coEvery { listedAssetRepository.resolveIdByTicker("PETR4", date) } returns 1
        coEvery { tradeService.create(any()) } returns mockk<Trade>()

        service.confirm(
            listOf(
                ImportedTradeConfirmation(
                    ticker = "PETR4",
                    date = date,
                    quantity = BigDecimal("100"),
                    price = BigDecimal("35.50"),
                ),
            ),
        )

        coVerify {
            tradeService.create(
                TradeCreation(assetId = 1, date = date, quantity = BigDecimal("100"), price = BigDecimal("35.50")),
            )
        }
    }

    "confirm should fail loudly when a ticker can't be resolved" {
        coEvery { listedAssetRepository.resolveIdByTicker("NOVA11", date) } returns null

        shouldThrow<BrokerageNoteParseException> {
            service.confirm(
                listOf(
                    ImportedTradeConfirmation(
                        ticker = "NOVA11",
                        date = date,
                        quantity = BigDecimal("5"),
                        price = BigDecimal("10.00"),
                    ),
                ),
            )
        }
    }
})

private fun order(ticker: String, kind: OrderKind) = Order(
    listedAssetId = 0,
    ticker = ticker,
    isFii = false,
    kind = kind,
    quantity = BigDecimal.ONE,
    notional = BigDecimal.ONE,
    contributions = emptyList(),
    dayTradeRisk = false,
)

private fun ceiling() = SaleCeiling(
    monthSold = BigDecimal.ZERO,
    limit = BigDecimal("20000.00"),
    remaining = BigDecimal("20000.00"),
    exceeded = false,
)
