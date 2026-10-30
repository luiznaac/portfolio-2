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
import dev.agner.portfolio.usecase.tax.model.CarrySeed
import dev.agner.portfolio.usecase.tax.model.RealizedKind
import dev.agner.portfolio.usecase.tax.model.TickerLedger
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class StockTaxCalculatorTest : StringSpec({

    val period = YearMonth.parse("2026-10")

    "an empty book reports a zero meter and no average prices" {
        val report = StockTaxCalculator.compute(period, emptyList(), CarrySeed.None)

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
            CarrySeed.None,
        )

        report.averagePrices.map { it.ticker } shouldBe listOf("HGLG11", "VALE3")
        report.averagePrices.first { it.ticker == "VALE3" }.averagePrice.plain() shouldBe "15"
        report.averagePrices.first { it.ticker == "HGLG11" }.quantity.plain() shouldBe "10"
    }

    "replays as of the period's end: a later trade never changes the period's basis" {
        val report = meter(
            stock(
                buy(date = "2026-10-01", quantity = "100", unitPrice = "10"),
                buy(id = 2, date = "2026-11-01", quantity = "100", unitPrice = "50"),
                sell(id = 3, date = "2026-10-05", quantity = "50", unitPrice = "20"),
            ),
        )

        report.meter.grossSales.plain() shouldBe "1000"
        report.meter.realizedGain.plain() shouldBe "500"
        report.averagePrices.single().quantity.plain() shouldBe "50"
        report.averagePrices.single().averagePrice.plain() shouldBe "10"
    }

    "a bucket at its adoption boundary is unavailable rather than double counted" {
        val report = reportOf(
            stock(
                buy(date = "2026-10-01", quantity = "2000", unitPrice = "10"),
                sell(id = 2, date = "2026-10-05", quantity = "2000", unitPrice = "10.5"),
            ),
            carrySeed = CarrySeed.adopted(
                openingBalances = mapOf(RealizedKind.SWING to BigDecimal("500")),
                through = YearMonth.parse("2026-10"),
            ),
        )

        val swing = report.carry.first { it.kind == RealizedKind.SWING }

        swing.available shouldBe false
        swing.openingBalance.shouldBeNull()
        swing.lossBanked.shouldBeNull()
        swing.lossApplied.shouldBeNull()
        swing.closingBalance.shouldBeNull()
    }

    "a bucket with no seed is available from zero" {
        val report = reportOf(
            stock(
                buy(date = "2026-10-01", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-10-05", quantity = "100", unitPrice = "12"),
            ),
            carrySeed = CarrySeed.None,
        )

        val swing = report.carry.first { it.kind == RealizedKind.SWING }

        swing.available shouldBe true
        swing.openingBalance!!.plain() shouldBe "0"
        swing.closingBalance!!.plain() shouldBe "0"
    }

    "an uncatalogued ticker held or sold in the period is reported as unclassified" {
        val report = meter(
            TickerLedger(
                ticker = "MYST3",
                assetKind = AssetKind.UNKNOWN,
                trades = listOf(
                    buy(ticker = "MYST3", quantity = "100", unitPrice = "10"),
                    sell(id = 2, ticker = "MYST3", quantity = "40", unitPrice = "12"),
                ),
            ),
            TickerLedger(
                ticker = "MYST4",
                assetKind = AssetKind.UNKNOWN,
                trades = listOf(
                    buy(id = 3, ticker = "MYST4", quantity = "100", unitPrice = "10"),
                    sell(id = 4, ticker = "MYST4", quantity = "100", unitPrice = "12"),
                ),
            ),
        )

        report.unclassified shouldBe listOf(
            dev.agner.portfolio.usecase.tax.model.TaxUnclassifiedTicker(
                ticker = "MYST3",
                status = dev.agner.portfolio.usecase.tax.model.TaxUnclassifiedStatus.HELD_AND_SOLD,
            ),
            dev.agner.portfolio.usecase.tax.model.TaxUnclassifiedTicker(
                ticker = "MYST4",
                status = dev.agner.portfolio.usecase.tax.model.TaxUnclassifiedStatus.SOLD,
            ),
        )
        report.meter.grossSales.plain() shouldBe "0"
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

    "the month splits its realized result into swing, day trade and FII" {
        val report = reportOf(
            stock(
                buy(ticker = "PETR4", date = "2026-10-01", quantity = "100", unitPrice = "10"),
                sell(id = 2, ticker = "PETR4", date = "2026-10-05", quantity = "100", unitPrice = "12"),
            ),
            stock(
                buy(id = 3, date = "2026-10-07", quantity = "100", unitPrice = "10"),
                sell(id = 4, date = "2026-10-07", quantity = "100", unitPrice = "11"),
                ticker = "VALE3",
            ),
            fii(
                buy(id = 5, date = "2026-09-01", quantity = "100", unitPrice = "150"),
                sell(id = 6, date = "2026-10-06", quantity = "100", unitPrice = "140"),
            ),
            carrySeed = CarrySeed.None,
        )

        val realized = report.realized.associateBy { it.kind }

        realized.getValue(RealizedKind.SWING).let {
            it.grossSales.plain() shouldBe "1200"
            it.gains.plain() shouldBe "200"
            it.losses.plain() shouldBe "0"
            it.result.plain() shouldBe "200"
        }
        realized.getValue(RealizedKind.DAY_TRADE).let {
            it.grossSales.plain() shouldBe "1100"
            it.gains.plain() shouldBe "100"
            it.losses.plain() shouldBe "0"
            it.result.plain() shouldBe "100"
        }
        realized.getValue(RealizedKind.FII).let {
            it.grossSales.plain() shouldBe "14000"
            it.gains.plain() shouldBe "0"
            it.losses.plain() shouldBe "1000"
            it.result.plain() shouldBe "-1000"
        }

        report.meter.grossSales.plain() shouldBe "1200"
        report.meter.realizedGain.plain() shouldBe "200"
    }

    "each carry bucket offsets its own kind's gain before tax" {
        val report = reportOf(
            stock(
                buy(date = "2026-10-01", quantity = "2000", unitPrice = "10"),
                sell(id = 2, date = "2026-10-05", quantity = "2000", unitPrice = "10.5"),
            ),
            stock(
                buy(id = 3, date = "2026-10-07", quantity = "100", unitPrice = "10"),
                sell(id = 4, date = "2026-10-07", quantity = "100", unitPrice = "11"),
                ticker = "VALE3",
            ),
            fii(
                buy(id = 5, date = "2026-09-01", quantity = "100", unitPrice = "150"),
                sell(id = 6, date = "2026-10-06", quantity = "100", unitPrice = "152"),
            ),
            carrySeed = CarrySeed.adopted(
                openingBalances = mapOf(
                    RealizedKind.SWING to BigDecimal("500"),
                    RealizedKind.DAY_TRADE to BigDecimal("300"),
                    RealizedKind.FII to BigDecimal("1000"),
                ),
                through = YearMonth.parse("2026-09"),
            ),
        )

        val carry = report.carry.associateBy { it.kind }

        carry.getValue(RealizedKind.SWING).let {
            it.openingBalance!!.plain() shouldBe "500"
            it.lossBanked!!.plain() shouldBe "0"
            it.lossApplied!!.plain() shouldBe "500"
            it.closingBalance!!.plain() shouldBe "0"
        }
        carry.getValue(RealizedKind.DAY_TRADE).let {
            it.openingBalance!!.plain() shouldBe "300"
            it.lossApplied!!.plain() shouldBe "100"
            it.closingBalance!!.plain() shouldBe "200"
        }
        carry.getValue(RealizedKind.FII).let {
            it.openingBalance!!.plain() shouldBe "1000"
            it.lossApplied!!.plain() shouldBe "200"
            it.closingBalance!!.plain() shouldBe "800"
        }

        report.meter.grossSales.plain() shouldBe "21000"
        report.meter.realizedGain.plain() shouldBe "1000"
        report.meter.taxableGain.plain() shouldBe "500"
        report.meter.tax.plain() shouldBe "75"
    }

    "an exempt month's gain leaves the swing carry untouched" {
        val report = reportOf(
            stock(
                buy(date = "2026-10-01", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-10-05", quantity = "100", unitPrice = "12"),
            ),
            carrySeed = CarrySeed.adopted(
                openingBalances = mapOf(RealizedKind.SWING to BigDecimal("500")),
                through = YearMonth.parse("2026-09"),
            ),
        )

        val swing = report.carry.first { it.kind == RealizedKind.SWING }

        swing.openingBalance!!.plain() shouldBe "500"
        swing.lossApplied!!.plain() shouldBe "0"
        swing.closingBalance!!.plain() shouldBe "500"
        report.meter.exceeded shouldBe false
        report.meter.taxableGain.plain() shouldBe "0"
        report.meter.tax.plain() shouldBe "0"
    }

    "a prior exempt month's loss banks into the current month" {
        val report = reportOf(
            stock(
                buy(date = "2026-09-01", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-09-05", quantity = "100", unitPrice = "6"),
                buy(id = 3, date = "2026-10-01", quantity = "2000", unitPrice = "10"),
                sell(id = 4, date = "2026-10-05", quantity = "2000", unitPrice = "10.5"),
            ),
            carrySeed = CarrySeed.None,
        )

        val swing = report.carry.first { it.kind == RealizedKind.SWING }

        swing.openingBalance!!.plain() shouldBe "400"
        swing.lossBanked!!.plain() shouldBe "0"
        swing.lossApplied!!.plain() shouldBe "400"
        swing.closingBalance!!.plain() shouldBe "0"
        report.meter.realizedGain.plain() shouldBe "1000"
        report.meter.taxableGain.plain() shouldBe "600"
        report.meter.tax.plain() shouldBe "90"
    }

    "a current month's loss banks even when the month is exempt" {
        val report = reportOf(
            stock(
                buy(date = "2026-10-01", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-10-05", quantity = "100", unitPrice = "7"),
            ),
            carrySeed = CarrySeed.adopted(
                openingBalances = mapOf(RealizedKind.SWING to BigDecimal("500")),
                through = YearMonth.parse("2026-09"),
            ),
        )

        val swing = report.carry.first { it.kind == RealizedKind.SWING }

        swing.openingBalance!!.plain() shouldBe "500"
        swing.lossBanked!!.plain() shouldBe "300"
        swing.lossApplied!!.plain() shouldBe "0"
        swing.closingBalance!!.plain() shouldBe "800"
        report.meter.taxableGain.plain() shouldBe "0"
    }

    "an FII loss only offsets FII gains" {
        val report = reportOf(
            stock(
                buy(date = "2026-10-01", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-10-05", quantity = "100", unitPrice = "12"),
            ),
            fii(
                buy(id = 3, date = "2026-09-01", quantity = "100", unitPrice = "150"),
                sell(id = 4, date = "2026-09-10", quantity = "100", unitPrice = "148"),
                buy(id = 5, date = "2026-10-01", quantity = "100", unitPrice = "150"),
                sell(id = 6, date = "2026-10-06", quantity = "100", unitPrice = "155"),
            ),
            carrySeed = CarrySeed.None,
        )

        val carry = report.carry.associateBy { it.kind }

        carry.getValue(RealizedKind.FII).let {
            it.openingBalance!!.plain() shouldBe "200"
            it.lossApplied!!.plain() shouldBe "200"
            it.closingBalance!!.plain() shouldBe "0"
        }
        carry.getValue(RealizedKind.SWING).let {
            it.openingBalance!!.plain() shouldBe "0"
            it.lossApplied!!.plain() shouldBe "0"
            it.closingBalance!!.plain() shouldBe "0"
        }
        report.meter.realizedGain.plain() shouldBe "200"
    }

    "the guard flags a same ticker, institution and date both-sides imported pair" {
        val report = reportOf(
            stock(
                buy(date = "2026-10-05", quantity = "100", unitPrice = "10", institutionId = 1),
                sell(id = 2, date = "2026-10-05", quantity = "100", unitPrice = "11", institutionId = 1),
            ),
            stock(
                buy(id = 3, date = "2026-10-06", quantity = "100", unitPrice = "10", institutionId = 1),
                sell(id = 4, date = "2026-10-06", quantity = "100", unitPrice = "11", institutionId = 2),
                ticker = "VALE3",
            ),
            fii(
                buy(id = 5, date = "2026-10-07", quantity = "100", unitPrice = "150"),
                sell(id = 6, date = "2026-10-07", quantity = "100", unitPrice = "151"),
            ),
            carrySeed = CarrySeed.None,
        )

        report.dayTrades.map { it.ticker to it.quantity.plain() } shouldBe
            listOf("PETR4" to "100", "HGLG11" to "100")
        report.dayTrades.map { it.institutionId } shouldBe listOf(1, 1)
    }

    "the guard ignores a manual same-day pair" {
        val report = reportOf(
            stock(
                buy(date = "2026-10-05", quantity = "100", unitPrice = "10", source = TradeSource.MANUAL),
                sell(
                    id = 2,
                    date = "2026-10-05",
                    quantity = "100",
                    unitPrice = "11",
                    source = TradeSource.MANUAL,
                ),
            ),
            carrySeed = CarrySeed.None,
        )

        report.dayTrades shouldBe emptyList()
        report.realized.first { it.kind == RealizedKind.DAY_TRADE }.result.plain() shouldBe "100"
    }

    "the guard only reports the open month's pairs" {
        val report = reportOf(
            stock(
                buy(date = "2026-09-05", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-09-05", quantity = "100", unitPrice = "11"),
            ),
            carrySeed = CarrySeed.None,
        )

        report.dayTrades shouldBe emptyList()
    }

    "the adoption boundary excludes ledger months already in the seeded balance" {
        val report = reportOf(
            stock(
                buy(date = "2026-09-01", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-09-05", quantity = "100", unitPrice = "6"),
                buy(id = 3, date = "2026-10-01", quantity = "2000", unitPrice = "10"),
                sell(id = 4, date = "2026-10-05", quantity = "2000", unitPrice = "10.5"),
            ),
            carrySeed = CarrySeed.adopted(
                openingBalances = mapOf(RealizedKind.SWING to BigDecimal("500")),
                through = YearMonth.parse("2026-09"),
            ),
        )

        val swing = report.carry.first { it.kind == RealizedKind.SWING }

        swing.openingBalance!!.plain() shouldBe "500"
        swing.lossApplied!!.plain() shouldBe "500"
        swing.closingBalance!!.plain() shouldBe "0"
        report.meter.taxableGain.plain() shouldBe "500"
        report.meter.tax.plain() shouldBe "75"
    }

    "a kind without a seed still rolls its own history past another kind's adoption" {
        val report = reportOf(
            stock(
                buy(date = "2026-09-01", quantity = "100", unitPrice = "10"),
                sell(id = 2, date = "2026-09-05", quantity = "100", unitPrice = "6"),
                buy(id = 3, date = "2026-10-01", quantity = "2000", unitPrice = "10"),
                sell(id = 4, date = "2026-10-05", quantity = "2000", unitPrice = "10.5"),
            ),
            fii(
                buy(id = 5, date = "2026-09-01", quantity = "100", unitPrice = "100"),
                sell(id = 6, date = "2026-09-10", quantity = "100", unitPrice = "90"),
            ),
            carrySeed = CarrySeed.adopted(
                openingBalances = mapOf(RealizedKind.SWING to BigDecimal("500")),
                through = YearMonth.parse("2026-09"),
            ),
        )

        val carry = report.carry.associateBy { it.kind }

        carry.getValue(RealizedKind.SWING).let {
            it.openingBalance!!.plain() shouldBe "500"
            it.lossApplied!!.plain() shouldBe "500"
            it.closingBalance!!.plain() shouldBe "0"
        }
        carry.getValue(RealizedKind.FII).let {
            it.openingBalance!!.plain() shouldBe "1000"
            it.lossBanked!!.plain() shouldBe "0"
            it.closingBalance!!.plain() shouldBe "1000"
        }
    }
})

private fun meter(vararg ledgers: TickerLedger) =
    StockTaxCalculator.compute(YearMonth.parse("2026-10"), ledgers.toList(), CarrySeed.None)

private fun reportOf(vararg ledgers: TickerLedger, carrySeed: CarrySeed) =
    StockTaxCalculator.compute(YearMonth.parse("2026-10"), ledgers.toList(), carrySeed)

private fun stock(
    vararg trades: Trade,
    ticker: String = "PETR4",
    corporateActions: List<CorporateAction> = emptyList(),
) = TickerLedger(
    ticker = ticker,
    assetKind = AssetKind.STOCK,
    trades = trades.toList(),
    corporateActions = corporateActions,
)

private fun fii(vararg trades: Trade) = TickerLedger(
    ticker = "HGLG11",
    assetKind = AssetKind.FII,
    trades = trades.toList(),
)
