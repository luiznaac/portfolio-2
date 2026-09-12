package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * Layout C — XP "Estratégia Quantitativa" (Top Dividendos Plus). The ticker list is printed three
 * times: the page-1 composition table, a page with an added Dividend Yield column, and a
 * "Desempenho dos ativos" table that still lists the names dropped this month. Only the first
 * counts.
 */
class XpQuantCarteiraReportParserTest : StringSpec({

    val resolver = StrategyReportParserResolver(listOf(XpQuantCarteiraReportParser(), GenericLineReportParser()))

    val report = arrayOf(
        "1XP RESEARCH",
        "Estratégia Quantitativa",
        "Carteira Top Dividendos Plus",
        "Performance. Retorno anualizado de +15,6% no período entre outubro de 2020 e hoje.",
        "Alterações em relação à última publicação: Entrada de IGTI11 e RENT3. Saída de VALE3 e ITUB4.",
        "1 de setembro de 2026",
        "Companhia (em ordem alfabética) Ticker Peso Setor",
        "Allos ALOS3 10% Income Properties",
        "Bradesco BBDC4 10% Banks",
        "Iguatemi IGTI11 10% Income Properties",
        "Estrategista Quantitativo",
        "Setembro 2026",
        // page with the Dividend Yield column — same tickers, must be de-duplicated
        "Companhia Ticker Peso Setor Dividend Yield 2026E",
        "Allos ALOS3 10% Income Properties 13.4%",
        "Bradesco BBDC4 10% Banks 8.8%",
        "Iguatemi IGTI11 10% Income Properties 5.6%",
        // "Desempenho dos ativos" — lists ITUB4 and VALE3, which left the portfolio this month
        "Companhia Ticker Setor Peso Data Desempenho",
        "Allos ALOS3 Income Properties 10% 01/04/2026 -9.2% -0.4% 2.1%",
        "Itaú Unibanco ITUB4 Banks 10% 01/07/2025 19.8% -7.8% 2.8%",
        "Vale VALE3 Metals & Mining 10% 01/12/2025 24.0% 4.9% 11.2%",
    )

    "extracts only the page-1 composition table, using the standalone reference month" {
        val parsed = resolver.parse(pdfOf(*report))

        parsed.referenceDate shouldBe LocalDate(2026, 9, 1)
        parsed.targets shouldContainExactly listOf(
            StrategyTarget("ALOS3", BigDecimal("0.1000")),
            StrategyTarget("BBDC4", BigDecimal("0.1000")),
            StrategyTarget("IGTI11", BigDecimal("0.1000")),
        )
    }

    "does not carry over the dropped tickers from the performance table" {
        val parsed = resolver.parse(pdfOf(*report))

        parsed.targets.map { it.ticker } shouldBe listOf("ALOS3", "BBDC4", "IGTI11")
    }
})
