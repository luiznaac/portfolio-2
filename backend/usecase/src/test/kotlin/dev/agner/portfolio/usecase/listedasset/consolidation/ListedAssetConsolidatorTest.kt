package dev.agner.portfolio.usecase.listedasset.consolidation

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.corporateaction.CorporateActionService
import dev.agner.portfolio.usecase.listedasset.ListedAssetService
import dev.agner.portfolio.usecase.listedasset.consolidation.model.ListedAssetConsolidationContext
import dev.agner.portfolio.usecase.listedasset.gateway.IQuoteGateway
import dev.agner.portfolio.usecase.listedasset.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.model.Quote
import dev.agner.portfolio.usecase.listedasset.model.QuoteSource
import dev.agner.portfolio.usecase.listedasset.position.model.ListedAssetPosition
import dev.agner.portfolio.usecase.listedasset.position.repository.IListedAssetPositionRepository
import dev.agner.portfolio.usecase.sell
import dev.agner.portfolio.usecase.trade.AveragePriceCalculator
import dev.agner.portfolio.usecase.trade.TradeService
import dev.agner.portfolio.usecase.trade.model.Trade
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.mockk.clearAllMocks
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class ListedAssetConsolidatorTest : StringSpec({

    val listedAssetService = mockk<ListedAssetService>()
    val tradeService = mockk<TradeService>()
    val corporateActionService = mockk<CorporateActionService>()
    val quoteGateway = mockk<IQuoteGateway>()
    val positionRepository = mockk<IListedAssetPositionRepository>(relaxUnitFun = true)
    val clock = Clock.fixed(Instant.parse("2026-09-15T12:00:00Z"), ZoneOffset.UTC)

    val consolidator = ListedAssetConsolidator(
        listedAssetService,
        tradeService,
        corporateActionService,
        quoteGateway,
        AveragePriceCalculator(),
        positionRepository,
        clock,
    )

    val tradeDate = LocalDate.parse("2026-01-02")
    val consolidationDate = LocalDate.parse("2026-09-15")

    beforeEach {
        clearAllMocks()
    }

    fun listedAsset(id: Int, ticker: String, kind: AssetKind) =
        ListedAsset(id = id, ticker = ticker, kind = kind, name = ticker, b3Identifier = ticker)

    fun context(asset: ListedAsset, trades: List<Trade>) = ListedAssetConsolidationContext(
        asset = asset,
        trades = trades,
        corporateActions = emptyList(),
    )

    fun quoteAt(price: String) = Quote(
        price = BigDecimal(price),
        date = consolidationDate,
        source = QuoteSource.BRAPI,
    )

    "should estimate stock tax at the swing capital-gains rate" {
        val asset = listedAsset(id = 1, ticker = "PETR4", kind = AssetKind.STOCK)
        val position = ListedAssetPosition(
            date = consolidationDate,
            principal = BigDecimal("1000.00"),
            yield = BigDecimal("200.00"),
            taxes = BigDecimal("30.00"),
        )

        coEvery { quoteGateway.getQuote(asset) } returns quoteAt("12.00")

        consolidator.consolidate(
            context(
                asset,
                listOf(buy(id = 1, assetId = asset.id, date = tradeDate, quantity = "100", price = "10.00")),
            ),
        )

        coVerify(exactly = 1) { positionRepository.save(asset.id, position) }
    }

    "should estimate FII tax at the FII capital-gains rate" {
        val asset = listedAsset(id = 1, ticker = "MXRF11", kind = AssetKind.FII)
        val position = ListedAssetPosition(
            date = consolidationDate,
            principal = BigDecimal("1000.00"),
            yield = BigDecimal("200.00"),
            taxes = BigDecimal("40.00"),
        )

        coEvery { quoteGateway.getQuote(asset) } returns quoteAt("12.00")

        consolidator.consolidate(
            context(
                asset,
                listOf(buy(id = 1, assetId = asset.id, date = tradeDate, quantity = "100", price = "10.00")),
            ),
        )

        coVerify(exactly = 1) { positionRepository.save(asset.id, position) }
    }

    "should estimate ETF and BDR tax at the stock capital-gains rate" {
        val assets = listOf(
            listedAsset(id = 2, ticker = "BOVA11", kind = AssetKind.ETF),
            listedAsset(id = 3, ticker = "AAPL34", kind = AssetKind.BDR),
        )
        val position = ListedAssetPosition(
            date = consolidationDate,
            principal = BigDecimal("1000.00"),
            yield = BigDecimal("200.00"),
            taxes = BigDecimal("30.00"),
        )

        assets.forEach { asset ->
            coEvery { quoteGateway.getQuote(asset) } returns quoteAt("12.00")

            consolidator.consolidate(
                context(
                    asset,
                    listOf(buy(id = 1, assetId = asset.id, date = tradeDate, quantity = "100", price = "10.00")),
                ),
            )

            coVerify(exactly = 1) { positionRepository.save(asset.id, position) }
        }
    }

    "should skip persisting a position with no open quantity" {
        val asset = listedAsset(id = 1, ticker = "PETR4", kind = AssetKind.STOCK)

        consolidator.consolidate(
            context(
                asset,
                listOf(
                    buy(id = 1, assetId = asset.id, date = tradeDate, quantity = "100", price = "10.00"),
                    sell(id = 2, assetId = asset.id, date = tradeDate, quantity = "100", price = "12.00"),
                ),
            ),
        )

        coVerify(exactly = 0) { positionRepository.save(any(), any()) }
    }

    "should fail when no quote is available" {
        val asset = listedAsset(id = 1, ticker = "PETR4", kind = AssetKind.STOCK)

        coEvery { quoteGateway.getQuote(asset) } returns null

        shouldThrow<IllegalStateException> {
            consolidator.consolidate(
                context(
                    asset,
                    listOf(buy(id = 1, assetId = asset.id, date = tradeDate, quantity = "100", price = "10.00")),
                ),
            )
        }

        coVerify(exactly = 0) { positionRepository.save(any(), any()) }
    }

    "should not tax an unrealized loss" {
        val asset = listedAsset(id = 1, ticker = "PETR4", kind = AssetKind.STOCK)
        val position = ListedAssetPosition(
            date = consolidationDate,
            principal = BigDecimal("1000.00"),
            yield = BigDecimal("-200.00"),
            taxes = BigDecimal("0.00"),
        )

        coEvery { quoteGateway.getQuote(asset) } returns quoteAt("8.00")

        consolidator.consolidate(
            context(
                asset,
                listOf(buy(id = 1, assetId = asset.id, date = tradeDate, quantity = "100", price = "10.00")),
            ),
        )

        coVerify(exactly = 1) { positionRepository.save(asset.id, position) }
    }
})
