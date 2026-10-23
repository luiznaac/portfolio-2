package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.buy
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.corporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.listedasset.model.Trade
import dev.agner.portfolio.usecase.listedasset.model.TradeSource
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.sell
import dev.agner.portfolio.usecase.tax.model.TickerLedger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.YearMonth

class StockTaxCalculatorTest : StringSpec({

    val period = YearMonth.parse("2026-10")

    "an empty book reports a zero meter and no average prices" {
        val report = StockTaxCalculator.compute(period, emptyList())

        report.period shouldBe period
        report.meter.grossSales.plain() shouldBe "0"
        report.meter.exceeded shouldBe false
        report.meter.tax.plain() shouldBe "0"
        report.averagePrices.shouldBeEmpty()
    }

    "the meter sums stock sales inside the month only" {
        val report = meter(
            stock(
                sell(date = "2026-09-30", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-10-05", quantity = "100", unitPrice = "10"),
                sell(id = 3, date = "2026-11-01", quantity = "100", unitPrice = "10"),
            ),
        )

        report.meter.grossSales.plain() shouldBe "1000"
        report.meter.realizedGain.plain() shouldBe "1000"
    }

    "buys never count toward the meter" {
        val report = meter(stock(buy(quantity = "5000", unitPrice = "10")))

        report.meter.grossSales.plain() shouldBe "0"
    }

    "only STOCK sales are metered" {
        val kinds = listOf(AssetKind.UNIT, AssetKind.FII, AssetKind.BDR, AssetKind.ETF, AssetKind.UNKNOWN)
        val report = meter(
            *kinds.mapIndexed { index, kind ->
                TickerLedger(
                    ticker = "OTHER$index",
                    assetKind = kind,
                    trades = listOf(
                        buy(ticker = "OTHER$index", quantity = "3000", unitPrice = "10"),
                        sell(id = 2, ticker = "OTHER$index", quantity = "3000", unitPrice = "10"),
                    ),
                )
            }.toTypedArray(),
        )

        report.meter.grossSales.plain() shouldBe "0"
        report.meter.realizedGain.plain() shouldBe "0"
        report.meter.exceeded shouldBe false
    }

    "a same-day round trip at one institution is a day trade and stays out of the meter" {
        val report = meter(
            stock(
                buy(date = "2026-10-05", quantity = "3000", unitPrice = "10"),
                sell(id = 2, date = "2026-10-05", quantity = "3000", unitPrice = "10"),
            ),
        )

        report.meter.grossSales.plain() shouldBe "0"
        report.meter.realizedGain.plain() shouldBe "0"
    }

    "only the quantity matched by a same-day buy is a day trade" {
        val report = meter(
            stock(
                buy(date = "2026-10-05", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-10-05", quantity = "300", unitPrice = "10"),
            ),
        )

        report.meter.grossSales.plain() shouldBe "2000"
    }

    "a same-day sale at another institution is not a day trade" {
        val report = meter(
            stock(
                buy(date = "2026-10-05", institutionId = 1, quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-10-05", institutionId = 2, quantity = "100", unitPrice = "10"),
            ),
        )

        report.meter.grossSales.plain() shouldBe "1000"
    }

    "imported and manual sales both count" {
        val report = meter(
            stock(
                sell(id = 1, quantity = "100", unitPrice = "10", source = TradeSource.B3_IMPORT),
                sell(id = 2, quantity = "100", unitPrice = "10", source = TradeSource.MANUAL),
            ),
        )

        report.meter.grossSales.plain() shouldBe "2000"
    }

    "exactly the exemption limit is still exempt" {
        val report = meter(
            stock(
                buy(quantity = "2000", unitPrice = "8"),
                sell(id = 2, quantity = "2000", unitPrice = "10"),
            ),
        )

        report.meter.grossSales.plain() shouldBe "20000"
        report.meter.exceeded shouldBe false
        report.meter.realizedGain.plain() shouldBe "4000"
        report.meter.taxableGain.plain() shouldBe "0"
        report.meter.tax.plain() shouldBe "0"
    }

    "crossing the limit taxes the whole month's gain at 15%" {
        val report = meter(
            stock(
                buy(quantity = "2001", unitPrice = "8"),
                sell(id = 2, quantity = "2001", unitPrice = "10"),
            ),
        )

        report.meter.grossSales.plain() shouldBe "20010"
        report.meter.exceeded shouldBe true
        report.meter.realizedGain.plain() shouldBe "4002"
        report.meter.taxableGain.plain() shouldBe "4002"
        report.meter.rate.plain() shouldBe "15"
        report.meter.tax.plain() shouldBe "600.3"
    }

    "the gain is measured against the weighted average price" {
        val report = meter(
            stock(
                buy(quantity = "100", unitPrice = "10"),
                buy(id = 2, quantity = "100", unitPrice = "20"),
                sell(id = 3, quantity = "100", unitPrice = "20"),
            ),
        )

        report.meter.realizedGain.plain() shouldBe "500"
    }

    "a split adjusts the average the gain is measured against" {
        val report = meter(
            stock(
                buy(date = "2026-10-01", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-10-15", quantity = "100", unitPrice = "6"),
                corporateActions = listOf(
                    corporateAction(CorporateActionKind.SPLIT, exDate = "2026-10-10", ratio = "2"),
                ),
            ),
        )

        report.meter.realizedGain.plain() shouldBe "100"
    }

    "a month with a net loss has no taxable gain even when the meter is exceeded" {
        val report = meter(
            stock(
                buy(quantity = "2100", unitPrice = "10"),
                sell(id = 2, quantity = "2000", unitPrice = "10"),
                sell(id = 3, quantity = "100", unitPrice = "8"),
            ),
        )

        report.meter.grossSales.plain() shouldBe "20800"
        report.meter.exceeded shouldBe true
        report.meter.realizedGain.plain() shouldBe "-200"
        report.meter.taxableGain.plain() shouldBe "0"
        report.meter.tax.plain() shouldBe "0"
    }

    "average prices list every held position, sorted by ticker" {
        val report = StockTaxCalculator.compute(
            period,
            listOf(
                TickerLedger(
                    ticker = "VALE3",
                    assetKind = AssetKind.STOCK,
                    trades = listOf(
                        buy(ticker = "VALE3", quantity = "100", unitPrice = "10"),
                        buy(id = 2, ticker = "VALE3", quantity = "100", unitPrice = "20"),
                    ),
                ),
                TickerLedger(
                    ticker = "HGLG11",
                    assetKind = AssetKind.FII,
                    trades = listOf(buy(ticker = "HGLG11", quantity = "10", unitPrice = "150")),
                ),
                TickerLedger(
                    ticker = "SOLD3",
                    assetKind = AssetKind.STOCK,
                    trades = listOf(
                        buy(ticker = "SOLD3", quantity = "100", unitPrice = "10"),
                        sell(id = 2, ticker = "SOLD3", quantity = "100", unitPrice = "12"),
                    ),
                ),
            ),
        )

        report.averagePrices.map { it.ticker } shouldBe listOf("HGLG11", "VALE3")
        report.averagePrices.first { it.ticker == "VALE3" }.averagePrice.plain() shouldBe "15"
        report.averagePrices.first { it.ticker == "HGLG11" }.quantity.plain() shouldBe "10"
    }

    "a sale's stored fee does not change the gross basis" {
        val report = meter(
            stock(
                buy(quantity = "100", unitPrice = "10", fee = "5"),
                sell(id = 2, quantity = "100", unitPrice = "12", fee = "5"),
            ),
        )

        report.meter.grossSales.plain() shouldBe "1200"
        report.meter.realizedGain.plain() shouldBe "200"
    }
})

private fun meter(vararg ledgers: TickerLedger) =
    StockTaxCalculator.compute(YearMonth.parse("2026-10"), ledgers.toList())

private fun stock(
    vararg trades: Trade,
    corporateActions: List<CorporateAction> = emptyList(),
) = TickerLedger(
    ticker = "PETR4",
    assetKind = AssetKind.STOCK,
    trades = trades.toList(),
    corporateActions = corporateActions,
)
