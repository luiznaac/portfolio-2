package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.httpapi.import.strategy.StrategyReportParserResolver
import dev.agner.portfolio.httpapi.import.strategy.XpEquityCarteiraParser
import dev.agner.portfolio.httpapi.import.strategy.XpFiiCarteiraParser
import dev.agner.portfolio.httpapi.import.strategy.XpGenericReportParser
import dev.agner.portfolio.httpapi.import.strategy.XpQuantCarteiraParser
import dev.agner.portfolio.usecase.plain
import dev.agner.portfolio.usecase.strategy.model.Rating
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.YearMonth

// The XP report the assistant's acceptance scenario uploads is committed under docs/acceptance, so
// it is pinned against the same resolver the running app uses. A PDF that stops being claimed, or
// whose staged entries drift from the enumerated diff, fails here instead of as a red browser run.
class XpAcceptanceFixtureTest : DescribeSpec({

    describe("the committed XP report acceptance fixture") {

        val fixture = acceptanceDocsFixture("xp-relatorio-2026-10.pdf")
        val resolver = StrategyReportParserResolver(
            listOf(
                XpEquityCarteiraParser(),
                XpQuantCarteiraParser(),
                XpFiiCarteiraParser(),
                XpGenericReportParser(),
            ),
        )
        val report = resolver.resolve(fixture.name, fixture.readBytes())

        it("is claimed by the equity layout parser and names its reference month") {
            report.parser shouldBe "XpEquityCarteira"
            report.referenceMonth shouldBe YearMonth.parse("2026-10")
        }

        it("stages the five enumerated entries with weight, rating and target price") {
            report.entries.map { entry ->
                listOf(
                    entry.ticker,
                    entry.weight.plain(),
                    entry.rating,
                    entry.targetPrice?.plain(),
                    entry.doNotTrade,
                )
            } shouldBe listOf(
                listOf("PETR4", "25", Rating.BUY, "60", false),
                listOf("VALE3", "22.5", Rating.NEUTRAL, "85", false),
                listOf("ITUB4", "20", Rating.SELL, "30", false),
                listOf("EGIE3", "20", Rating.BUY, "52", false),
                listOf("CXSE3", "12.5", Rating.BUY, "20", false),
            )
        }
    }
})
