package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.bd
import dev.agner.portfolio.usecase.catalog.model.AssetClass
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.catalog.model.CatalogEntry
import dev.agner.portfolio.usecase.harvestLeg
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.order.model.OrderSide
import dev.agner.portfolio.usecase.order.model.SuggestedOrder
import dev.agner.portfolio.usecase.order.model.SuggestedOrderStatus
import dev.agner.portfolio.usecase.order.repository.ISuggestedOrderRepository
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.portfolio.MonthService
import dev.agner.portfolio.usecase.portfolio.model.CurrentMonth
import dev.agner.portfolio.usecase.portfolio.model.Month
import dev.agner.portfolio.usecase.portfolio.model.MonthStatus
import dev.agner.portfolio.usecase.portfolio.model.Targets
import dev.agner.portfolio.usecase.quotes.QuoteService
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import dev.agner.portfolio.usecase.tax.model.ExemptionMeter
import dev.agner.portfolio.usecase.tax.model.HarvestPair
import dev.agner.portfolio.usecase.tax.model.HarvestPairCreation
import dev.agner.portfolio.usecase.tax.model.StockTaxReport
import dev.agner.portfolio.usecase.tax.model.TickerAveragePrice
import dev.agner.portfolio.usecase.tax.repository.IHarvestOptOutRepository
import dev.agner.portfolio.usecase.tax.repository.IHarvestPairRepository
import dev.agner.portfolio.usecase.tickerAveragePrice
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import io.mockk.Runs
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.just
import io.mockk.mockk
import kotlinx.datetime.LocalDate
import kotlinx.datetime.YearMonth
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

class HarvestServiceTest : StringSpec({

    "returns null when no month is open" {
        service(months = months(null)).fetch().shouldBeNull()
    }

    "sizes the pass from the month's remaining exemption headroom" {
        val service = service(
            taxReport = taxReport(
                grossSales = "15000",
                averagePrices = listOf(tickerAveragePrice(ticker = "PETR4", quantity = "300", averagePrice = "12")),
            ),
        )

        val report = requireNotNull(service.fetch())

        report.period shouldBe YearMonth.parse("2026-10")
        report.headroom.plain() shouldBe "5000"
        report.minimumLoss.plain() shouldBe "500"
        report.suggestions.single().ticker shouldBe "PETR4"
        report.suggestions.single().sell.date shouldBe LocalDate.parse("2026-10-22")
        report.suggestions.single().rebuy.date shouldBe LocalDate.parse("2026-10-23")
    }

    "reduces the headroom and the custody by the plan's still-unexecuted stock sells" {
        val service = service(
            taxReport = taxReport(
                grossSales = "15000",
                averagePrices = listOf(
                    tickerAveragePrice(ticker = "PETR4", quantity = "500", averagePrice = "12"),
                ),
            ),
            suggestedOrders = suggestedOrders(suggestedSell(ticker = "PETR4", quantity = "100")),
            sliceQuantity = "500",
        )

        val report = requireNotNull(service.fetch())

        report.headroom.plain() shouldBe "4000"
        report.suggestions.single().quantity.plain() shouldBe "400"
        report.suggestions.single().loss.plain() shouldBe "800"
    }

    "removes a ticker that already has an accepted pair from the suggestions" {
        val service = service(pairs = pairs(storedPair))

        val report = requireNotNull(service.fetch())

        report.suggestions.shouldBeEmpty()
        report.pairs.single().ticker shouldBe "PETR4"
    }

    "refuses a second pair for the same ticker in the month" {
        val service = service(pairs = pairs(storedPair), taxReport = sizeablePosition())

        val failure = shouldThrow<InvalidHarvestPairException> {
            service.accept(pairCreation())
        }

        failure.detail shouldBe "PETR4 already has an accepted pair this month"
    }

    "accepting stores the pair with the loss at the fiscal average price" {
        val pairs = pairs()
        val service = service(pairs = pairs, taxReport = sizeablePosition())

        service.accept(pairCreation(ticker = "petr4"))

        coVerify(exactly = 1) {
            pairs.save(
                1,
                match { it.ticker == "PETR4" && it.strategyId == 7 },
                match { it.plain() == "600" },
            )
        }
    }

    "refuses a non-stock ticker" {
        val service = service(
            taxReport = taxReport(
                averagePrices = listOf(
                    tickerAveragePrice(ticker = "HGLG11", quantity = "100", averagePrice = "100"),
                ),
            ),
        )

        val failure = shouldThrow<InvalidHarvestPairException> {
            service.accept(pairCreation(ticker = "HGLG11", strategyId = null))
        }

        failure.detail shouldBe "HGLG11 is FII; only STOCK positions are harvestable"
    }

    "refuses a same-day rebuy" {
        val service = service(taxReport = sizeablePosition())

        val failure = shouldThrow<InvalidHarvestPairException> {
            service.accept(pairCreation(rebuyDate = "2026-10-22"))
        }

        failure.detail shouldBe "the rebuy must be on a later day than the sell"
    }

    "refuses a weekend leg" {
        val service = service(taxReport = sizeablePosition())

        val failure = shouldThrow<InvalidHarvestPairException> {
            service.accept(pairCreation(rebuyDate = "2026-10-24"))
        }

        failure.detail shouldBe "both legs must fall on a business day"
    }

    "refuses an opted-out ticker" {
        val service = service(optOuts = optOuts("PETR4"), taxReport = sizeablePosition())

        val failure = shouldThrow<InvalidHarvestPairException> {
            service.accept(pairCreation())
        }

        failure.detail shouldBe "PETR4 is opted out of harvest"
    }

    "refuses a sell that realizes no loss" {
        val service = service(taxReport = sizeablePosition())

        val failure = shouldThrow<InvalidHarvestPairException> {
            service.accept(pairCreation(sellPrice = "12"))
        }

        failure.detail shouldBe "the sell leg realizes no loss at PETR4's average price of 12"
    }

    "refuses a strategy slice the ticker does not hold" {
        val service = service(taxReport = sizeablePosition())

        val failure = shouldThrow<InvalidHarvestPairException> {
            service.accept(pairCreation(strategyId = 99))
        }

        failure.detail shouldBe "PETR4 has no Attribution slice for strategy 99"
    }

    "refuses an attributed ticker without a strategy slice" {
        val service = service(taxReport = sizeablePosition())

        val failure = shouldThrow<InvalidHarvestPairException> {
            service.accept(pairCreation(strategyId = null))
        }

        failure.detail shouldBe "PETR4 is attributed, so the pair needs a strategy slice"
    }

    "refuses a sell above the held quantity" {
        val service = service(taxReport = sizeablePosition())

        val failure = shouldThrow<InvalidHarvestPairException> {
            service.accept(pairCreation(sellQuantity = "501"))
        }

        failure.detail shouldBe "the sell leg trades 501, but PETR4 holds 500"
    }

    "refuses a sell above its Attribution slice" {
        val service = service(taxReport = sizeablePosition())

        val failure = shouldThrow<InvalidHarvestPairException> {
            service.accept(pairCreation(sellQuantity = "301"))
        }

        failure.detail shouldBe "strategy 7 holds 300 of PETR4, but the sell leg trades 301"
    }

    "persists an opt-out and returns the fresh report" {
        val optOuts = optOuts()
        val service = service(optOuts = optOuts)

        service.setOptOut("petr4", true)

        coVerify(exactly = 1) { optOuts.set("PETR4", true) }
    }

    "an opted-out candidate leaves the suggestions and shows as ignored" {
        val service = service(optOuts = optOuts("PETR4"))

        val report = requireNotNull(service.fetch())

        report.suggestions.shouldBeEmpty()
        report.ignored.single().ticker shouldBe "PETR4"
        report.ignored.single().loss.plain() shouldBe "600"
    }
})

private fun service(
    months: MonthService = months(),
    taxReport: StockTaxReport = taxReport(),
    optOuts: IHarvestOptOutRepository = optOuts(),
    pairs: IHarvestPairRepository = pairs(),
    suggestedOrders: ISuggestedOrderRepository = suggestedOrders(),
    sliceQuantity: String = "300",
): HarvestService {
    val quotes = mockk<QuoteService>()
    coEvery { quotes.latestClose(any()) } returns bd("10")

    return HarvestService(
        months = months,
        tax = mockk {
            coEvery { fetchCurrent() } returns taxReport
        },
        catalog = mockk {
            coEvery { fetchAll() } returns listOf(
                catalogEntry("PETR4", AssetKind.STOCK),
                catalogEntry("HGLG11", AssetKind.FII),
            )
        },
        quotes = quotes,
        attributions = mockk {
            coEvery { fetchSlicesByTicker() } returns mapOf(
                "PETR4" to listOf(AttributionSlice(7, "Dividendos", bd(sliceQuantity))),
            )
        },
        strategies = mockk {
            coEvery { fetchNeutroSlices() } returns setOf(StrategyTicker("Dividendos", "PETR4"))
        },
        suggestedOrders = suggestedOrders,
        optOuts = optOuts,
        harvestPairs = pairs,
        clock = Clock.fixed(Instant.parse("2026-10-22T12:00:00Z"), ZoneOffset.UTC),
    )
}

private fun months(current: CurrentMonth? = currentMonth): MonthService = mockk {
    coEvery { fetchCurrent() } returns current
}

private fun optOuts(vararg tickers: String): IHarvestOptOutRepository = mockk {
    coEvery { fetchAll() } returns tickers.toSet()
    coEvery { set(any(), any()) } just Runs
}

private fun pairs(vararg accepted: HarvestPair): IHarvestPairRepository = mockk {
    coEvery { fetchBy(1) } returns accepted.toList()
    coEvery { save(any(), any(), any()) } returns storedPair
}

private fun suggestedOrders(vararg orders: SuggestedOrder): ISuggestedOrderRepository = mockk {
    coEvery { fetchBy(1) } returns orders.toList()
}

private fun taxReport(
    grossSales: String = "15000",
    averagePrices: List<TickerAveragePrice> = listOf(
        tickerAveragePrice(ticker = "PETR4", quantity = "300", averagePrice = "12"),
    ),
) = StockTaxReport(
    period = YearMonth.parse("2026-10"),
    meter = ExemptionMeter(
        grossSales = bd(grossSales),
        exemptionLimit = BigDecimal("20000"),
        exceeded = bd(grossSales) > BigDecimal("20000"),
        realizedGain = BigDecimal.ZERO,
        taxableGain = BigDecimal.ZERO,
        rate = BigDecimal("15"),
        tax = BigDecimal.ZERO,
    ),
    averagePrices = averagePrices,
    realized = emptyList(),
    carry = emptyList(),
    dayTrades = emptyList(),
)

private fun sizeablePosition() = taxReport(
    averagePrices = listOf(
        tickerAveragePrice(ticker = "PETR4", quantity = "500", averagePrice = "12"),
    ),
)

private fun suggestedSell(
    ticker: String = "PETR4",
    quantity: String = "100",
    price: String = "10",
) = SuggestedOrder(
    id = 1,
    ticker = ticker,
    side = OrderSide.SELL,
    quantity = bd(quantity),
    suggestedPrice = bd(price),
    executedQuantity = BigDecimal.ZERO,
    status = SuggestedOrderStatus.SUGGESTED,
    matchedTradeId = null,
)

private fun catalogEntry(ticker: String, kind: AssetKind) = CatalogEntry(
    ticker = ticker,
    shortName = ticker,
    bdiType = "??",
    isin = null,
    assetKind = kind,
    assetClass = AssetClass.ACOES,
    firstSeen = LocalDate.parse("2026-01-01"),
    lastSeen = LocalDate.parse("2026-10-01"),
)

private fun pairCreation(
    ticker: String = "PETR4",
    strategyId: Int? = 7,
    sellDate: String = "2026-10-22",
    sellQuantity: String = "300",
    sellPrice: String = "10",
    rebuyDate: String = "2026-10-23",
    rebuyQuantity: String = "300",
    rebuyPrice: String = "10",
) = HarvestPairCreation(
    ticker = ticker,
    strategyId = strategyId,
    sellDate = LocalDate.parse(sellDate),
    sellQuantity = bd(sellQuantity),
    sellPrice = bd(sellPrice),
    rebuyDate = LocalDate.parse(rebuyDate),
    rebuyQuantity = bd(rebuyQuantity),
    rebuyPrice = bd(rebuyPrice),
)

private val storedPair = HarvestPair(
    id = 1,
    ticker = "PETR4",
    strategyId = 7,
    strategy = "Dividendos",
    loss = bd("600"),
    sell = harvestLeg("2026-10-22"),
    rebuy = harvestLeg("2026-10-23"),
)

private val currentMonth = CurrentMonth(
    month = Month(
        id = 1,
        period = YearMonth.parse("2026-10"),
        capital = bd("100000"),
        floor = bd("10000"),
        status = MonthStatus.OPEN,
    ),
    targets = Targets(
        classes = emptyList(),
        strategies = emptyList(),
        subClasses = emptyList(),
        tickers = emptyList(),
    ),
)
