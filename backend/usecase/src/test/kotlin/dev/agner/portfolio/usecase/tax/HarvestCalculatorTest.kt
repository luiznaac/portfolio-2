package dev.agner.portfolio.usecase.tax

import dev.agner.portfolio.usecase.bd
import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.listedasset.model.AttributionSlice
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.strategy.model.StrategyTicker
import dev.agner.portfolio.usecase.tax.model.HarvestContext
import dev.agner.portfolio.usecase.tax.model.TickerAveragePrice
import dev.agner.portfolio.usecase.tickerAveragePrice
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldBeEmpty
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate

class HarvestCalculatorTest : StringSpec({

    "ranks harvest candidates by unrealized loss descending" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(
                    tickerAveragePrice(ticker = "AAAA3", quantity = "100", averagePrice = "10"),
                    tickerAveragePrice(ticker = "BBBB3", quantity = "100", averagePrice = "12"),
                    tickerAveragePrice(ticker = "CCCC3", quantity = "100", averagePrice = "11"),
                ),
                prices = mapOf("AAAA3" to bd("8"), "BBBB3" to bd("9"), "CCCC3" to bd("10")),
                headroom = "100000",
                minimumLoss = "100",
            ),
        )

        report.suggestions.map { it.ticker } shouldBe listOf("BBBB3", "AAAA3", "CCCC3")
    }

    "sizes each pair to the remaining R$20k headroom and realizes the loss" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(tickerAveragePrice(ticker = "PETR4", quantity = "1000", averagePrice = "12")),
                prices = mapOf("PETR4" to bd("10")),
                headroom = "3000",
                minimumLoss = "500",
                sellDate = "2026-10-23",
            ),
        )

        val proposal = report.suggestions.single()
        proposal.quantity.plain() shouldBe "300"
        proposal.loss.plain() shouldBe "600"
        proposal.sell.quantity.plain() shouldBe "300"
        proposal.sell.amount.plain() shouldBe "3000"
        proposal.sell.price.plain() shouldBe "10"
        proposal.sell.date shouldBe LocalDate.parse("2026-10-23")
        proposal.rebuy.quantity.plain() shouldBe "300"
        proposal.rebuy.amount.plain() shouldBe "3000"
    }

    "the first pair consumes the headroom before the next candidate is sized" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(
                    tickerAveragePrice(ticker = "PETR4", quantity = "1000", averagePrice = "12"),
                    tickerAveragePrice(ticker = "VALE3", quantity = "100", averagePrice = "11"),
                ),
                prices = mapOf("PETR4" to bd("10"), "VALE3" to bd("10")),
                headroom = "3000",
                minimumLoss = "500",
            ),
        )

        report.suggestions.map { it.ticker } shouldBe listOf("PETR4")
        report.suggestions.single().quantity.plain() shouldBe "300"
    }

    "drops a candidate whose headroom-sized loss is below the minimum-gain gate" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(tickerAveragePrice(ticker = "PETR4", quantity = "1000", averagePrice = "12")),
                prices = mapOf("PETR4" to bd("10")),
                headroom = "2000",
                minimumLoss = "500",
            ),
        )

        report.suggestions.shouldBeEmpty()
    }

    "rebuy lands on the next business day, never the same day" {
        val friday = HarvestCalculator.compute(
            context(
                positions = listOf(tickerAveragePrice(ticker = "PETR4", quantity = "300", averagePrice = "12")),
                prices = mapOf("PETR4" to bd("10")),
                headroom = "100000",
                minimumLoss = "500",
                sellDate = "2026-10-23",
            ),
        )
        friday.suggestions.single().rebuy.date shouldBe LocalDate.parse("2026-10-26")

        val thursday = HarvestCalculator.compute(
            context(
                positions = listOf(tickerAveragePrice(ticker = "PETR4", quantity = "300", averagePrice = "12")),
                prices = mapOf("PETR4" to bd("10")),
                headroom = "100000",
                minimumLoss = "500",
                sellDate = "2026-10-22",
            ),
        )
        thursday.suggestions.single().rebuy.date shouldBe LocalDate.parse("2026-10-23")
    }

    "allows harvesting a NEUTRO slice and reports the flag" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(tickerAveragePrice(ticker = "PETR4", quantity = "300", averagePrice = "12")),
                prices = mapOf("PETR4" to bd("10")),
                headroom = "100000",
                minimumLoss = "500",
                slices = mapOf("PETR4" to listOf(AttributionSlice(7, "Dividendos", bd("300")))),
                neutro = setOf(StrategyTicker("Dividendos", "PETR4")),
            ),
        )

        val proposal = report.suggestions.single()
        proposal.neutro shouldBe true
        proposal.strategyId shouldBe 7
        proposal.strategy shouldBe "Dividendos"
    }

    "caps the sell at the largest Attribution slice so the rebuy returns to it" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(tickerAveragePrice(ticker = "PETR4", quantity = "100", averagePrice = "12")),
                prices = mapOf("PETR4" to bd("10")),
                headroom = "100000",
                minimumLoss = "100",
                slices = mapOf(
                    "PETR4" to listOf(
                        AttributionSlice(7, "Dividendos", bd("60")),
                        AttributionSlice(8, "Small Caps", bd("40")),
                    ),
                ),
            ),
        )

        val proposal = report.suggestions.single()
        proposal.quantity.plain() shouldBe "60"
        proposal.strategyId shouldBe 7
        proposal.rebuy.quantity.plain() shouldBe "60"
    }

    "ranks by the position's whole loss, not only its largest slice" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(
                    tickerAveragePrice(ticker = "AAAA3", quantity = "100", averagePrice = "13"),
                    tickerAveragePrice(ticker = "BBBB3", quantity = "10", averagePrice = "12"),
                ),
                prices = mapOf("AAAA3" to bd("10"), "BBBB3" to bd("10")),
                headroom = "100000",
                minimumLoss = "1",
                slices = mapOf("AAAA3" to listOf(AttributionSlice(7, "Dividendos", bd("10")))),
            ),
        )

        report.suggestions.map { it.ticker } shouldBe listOf("AAAA3", "BBBB3")
    }

    "reports an opted-out ticker as ignored instead of proposing it" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(tickerAveragePrice(ticker = "PETR4", quantity = "300", averagePrice = "12")),
                prices = mapOf("PETR4" to bd("10")),
                headroom = "100000",
                minimumLoss = "500",
                optOuts = setOf("PETR4"),
            ),
        )

        report.suggestions.shouldBeEmpty()
        report.ignored.single().ticker shouldBe "PETR4"
        report.ignored.single().loss.plain() shouldBe "600"
    }

    "reports FIIs with the 20% estimated liability and never proposes them" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(
                    tickerAveragePrice(ticker = "HGLG11", quantity = "100", averagePrice = "100"),
                    tickerAveragePrice(ticker = "KNRI11", quantity = "100", averagePrice = "100"),
                ),
                prices = mapOf("HGLG11" to bd("110"), "KNRI11" to bd("90")),
                kinds = mapOf("HGLG11" to AssetKind.FII, "KNRI11" to AssetKind.FII),
                headroom = "100000",
                minimumLoss = "500",
            ),
        )

        report.suggestions.shouldBeEmpty()
        report.fiiReport.map { it.ticker } shouldBe listOf("HGLG11", "KNRI11")
        report.fiiReport[0].unrealizedResult.plain() shouldBe "1000"
        report.fiiReport[0].estimatedTax.plain() shouldBe "200"
        report.fiiReport[1].unrealizedResult.plain() shouldBe "-1000"
        report.fiiReport[1].estimatedTax.plain() shouldBe "0"
    }

    "ignores units, ETFs, BDRs and uncatalogued tickers" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(
                    tickerAveragePrice(ticker = "SANB11", quantity = "100", averagePrice = "30"),
                    tickerAveragePrice(ticker = "BOVA11", quantity = "100", averagePrice = "110"),
                    tickerAveragePrice(ticker = "AAPL34", quantity = "100", averagePrice = "50"),
                    tickerAveragePrice(ticker = "MYST3", quantity = "100", averagePrice = "20"),
                ),
                prices = mapOf(
                    "SANB11" to bd("20"),
                    "BOVA11" to bd("100"),
                    "AAPL34" to bd("40"),
                    "MYST3" to bd("10"),
                ),
                kinds = mapOf(
                    "SANB11" to AssetKind.UNIT,
                    "BOVA11" to AssetKind.ETF,
                    "AAPL34" to AssetKind.BDR,
                    "MYST3" to AssetKind.UNKNOWN,
                ),
                headroom = "100000",
                minimumLoss = "100",
            ),
        )

        report.suggestions.shouldBeEmpty()
        report.ignored.shouldBeEmpty()
        report.fiiReport.shouldBeEmpty()
    }

    "drops a ticker that already has an accepted pair" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(tickerAveragePrice(ticker = "PETR4", quantity = "300", averagePrice = "12")),
                prices = mapOf("PETR4" to bd("10")),
                headroom = "100000",
                minimumLoss = "500",
                accepted = setOf("PETR4"),
            ),
        )

        report.suggestions.shouldBeEmpty()
        report.ignored.shouldBeEmpty()
    }

    "proposes nothing once the month's exemption headroom is exhausted" {
        val report = HarvestCalculator.compute(
            context(
                positions = listOf(tickerAveragePrice(ticker = "PETR4", quantity = "300", averagePrice = "12")),
                prices = mapOf("PETR4" to bd("10")),
                headroom = "0",
                minimumLoss = "500",
            ),
        )

        report.suggestions.shouldBeEmpty()
    }
})

private fun context(
    positions: List<TickerAveragePrice>,
    prices: Map<String, java.math.BigDecimal>,
    headroom: String,
    minimumLoss: String,
    sellDate: String = "2026-10-22",
    kinds: Map<String, AssetKind> = emptyMap(),
    slices: Map<String, List<AttributionSlice>> = emptyMap(),
    neutro: Set<StrategyTicker> = emptySet(),
    optOuts: Set<String> = emptySet(),
    accepted: Set<String> = emptySet(),
) = HarvestContext(
    sellDate = LocalDate.parse(sellDate),
    headroom = bd(headroom),
    positions = positions,
    prices = prices,
    assetKinds = positions.associate { it.ticker to (kinds[it.ticker] ?: AssetKind.STOCK) },
    slicesByTicker = slices,
    neutroSlices = neutro,
    optOuts = optOuts,
    acceptedTickers = accepted,
    minimumLoss = bd(minimumLoss),
)
