package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.strategy.InvalidStrategyReportException
import dev.agner.portfolio.usecase.strategy.UnsupportedStrategyReportException
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.Rating
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.YearMonth
import java.math.BigDecimal

class XpStrategyReportParserTest : StringSpec({
    val equity = XpEquityCarteiraParser()
    val quant = XpQuantCarteiraParser()
    val fii = XpFiiCarteiraParser()
    val generic = XpGenericReportParser()
    val resolver = StrategyReportParserResolver(listOf(equity, quant, fii, generic))

    "the equity parser reads the Top Dividendos portfolio table" {
        val report = equity.parse("top-dividendos.pdf", pages("top-dividendos.txt"))

        report.referenceMonth shouldBe YearMonth.parse("2026-09")
        report.entries.size shouldBe 11
        report.entries.first { it.ticker == "PETR4" } shouldBe
            EditionEntry("PETR4", BigDecimal("12.5"), Rating.BUY, BigDecimal("63.00"))
        report.entries.first { it.ticker == "B3SA3" } shouldBe
            EditionEntry("B3SA3", BigDecimal("10.0"), Rating.NEUTRAL, BigDecimal("16.00"))
        weightSum(report) shouldBe BigDecimal("100.0")
    }

    "the equity parser does not read the performance table's stale weights" {
        val report = equity.parse("top-dividendos.pdf", pages("top-dividendos.txt"))

        // Page 3 still lists ITUB4 at 15,00% (last month's weight); only page 1's table is read.
        report.entries.first { it.ticker == "ITUB4" }.weight shouldBe BigDecimal("10.0")
    }

    "the quant parser reads the Dividendos Plus equal-weight table without a rating" {
        val report = quant.parse("plus.pdf", pages("top-dividendos-plus.txt"))

        report.referenceMonth shouldBe YearMonth.parse("2026-09")
        report.entries.size shouldBe 10
        report.entries.first { it.ticker == "ALOS3" } shouldBe EditionEntry("ALOS3", BigDecimal("10"), null, null)
        report.entries.all { it.rating == null && it.targetPrice == null } shouldBe true
        weightSum(report) shouldBe BigDecimal("100")
    }

    "the FII parser reads the Fundamentalista table" {
        val report = fii.parse("fii.pdf", pages("fundamentalista-fii.txt"))

        report.referenceMonth shouldBe YearMonth.parse("2026-09")
        report.entries.size shouldBe 16
        report.entries.first { it.ticker == "MCCI11" } shouldBe
            EditionEntry("MCCI11", BigDecimal("9.00"), Rating.BUY, null)
        weightSum(report) shouldBe BigDecimal("100.00")
    }

    "each concrete parser claims only its own layout" {
        val equityPages = pages("top-dividendos.txt")
        val quantPages = pages("top-dividendos-plus.txt")
        val fiiPages = pages("fundamentalista-fii.txt")

        equity.claims(equityPages) shouldBe true
        equity.claims(quantPages) shouldBe false
        equity.claims(fiiPages) shouldBe false

        quant.claims(quantPages) shouldBe true
        quant.claims(equityPages) shouldBe false
        quant.claims(fiiPages) shouldBe false

        fii.claims(fiiPages) shouldBe true
        fii.claims(equityPages) shouldBe false
        fii.claims(quantPages) shouldBe false
    }

    "a report whose weights do not sum to 100 fails loudly" {
        val page = """
            Carteira Top Dividendos 1 de setembro de 2026
            Companhia Ticker Peso Rating Preço-Alvo
            Petrobras PETR4 10,0% Compra R$ 63,00
        """.trimIndent()

        shouldThrow<InvalidStrategyReportException> {
            equity.parse("broken.pdf", listOf(page))
        }
    }

    "a report with no reference month fails loudly" {
        val page = """
            Companhia Ticker Peso Rating Preço-Alvo
            Petrobras PETR4 100,0% Compra R$ 63,00
        """.trimIndent()

        shouldThrow<InvalidStrategyReportException> {
            equity.parse("undated.pdf", listOf(page))
        }
    }

    "the generic fallback reads an unknown layout's ticker and weight pairs" {
        val page = """
            Carteira Nova de setembro de 2026
            ABCX11 25%
            DEFY3 35,5%
            GHIW11 39,5%
        """.trimIndent()

        val report = generic.parse("unknown.pdf", listOf(page))

        report.referenceMonth shouldBe YearMonth.parse("2026-09")
        report.entries.map { it.ticker to it.weight } shouldBe listOf(
            "ABCX11" to BigDecimal("25"),
            "DEFY3" to BigDecimal("35.5"),
            "GHIW11" to BigDecimal("39.5"),
        )
    }

    "the resolver dispatches each real layout to its parser" {
        resolver.resolvePages("top-dividendos.pdf", pages("top-dividendos.txt")).entries.size shouldBe 11
        resolver.resolvePages("plus.pdf", pages("top-dividendos-plus.txt")).entries.size shouldBe 10
        resolver.resolvePages("fii.pdf", pages("fundamentalista-fii.txt")).entries.size shouldBe 16
    }

    "the resolver falls back to the generic parser for an unknown layout" {
        val page = "Carteira Nova de setembro de 2026\nABCX11 100%"

        resolver.resolvePages("unknown.pdf", listOf(page)).entries.single().ticker shouldBe "ABCX11"
    }

    "the resolver rejects a file that is not a report at all" {
        shouldThrow<UnsupportedStrategyReportException> {
            resolver.resolvePages("notes.pdf", listOf("Some unrelated prose with no tickers."))
        }
    }
})

private fun weightSum(report: dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport): BigDecimal =
    report.entries.fold(BigDecimal.ZERO) { total, entry -> total + entry.weight }

private fun pages(resource: String): List<String> =
    requireNotNull(object {}.javaClass.getResourceAsStream("/xp/$resource")) {
        "missing fixture /xp/$resource"
    }
        .bufferedReader()
        .readText()
        .split(Regex("(?m)^===== PAGE \\d+ =====\\s*$"))
        .map { it.trim() }
        .filter { it.isNotEmpty() }
