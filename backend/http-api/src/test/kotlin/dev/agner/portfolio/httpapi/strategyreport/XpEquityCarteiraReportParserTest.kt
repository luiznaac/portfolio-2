package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.collections.shouldContainExactly
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * Layout A — XP Equity Research "Carteira XP" (Top Ações, Top Dividendos, Top Small Caps). Lines
 * reproduce what pdfbox extracts from the real September 2026 reports, including the two traps:
 * sector weights printed before the ticker, and a page-3 "Desempenho de cada ativo" table that
 * lists every ticker again with entry-date weights and no rating.
 */
class XpEquityCarteiraReportParserTest : StringSpec({

    val resolver = StrategyReportParserResolver(listOf(XpEquityCarteiraReportParser(), GenericLineReportParser()))

    val page1 = arrayOf(
        "1EQUITY RESEARCH",
        "Estratégia | Carteira XP",
        "Carteira Top Dividendos XP",
        "Estamos aumentando o peso de PETR4 de 10% para 12,5%, principalmente para elevar a exposição.",
        "Estamos reduzindo o peso de ITUB4 de 15,0% para 10,0%, diante de um cenário mais desafiador.",
        "1 de setembro de 2026",
        "Setembro 2026",
        "Nesta carteira, recomendamos papéis de empresas boas pagadoras de dividendos.",
        "Segmento Setor Peso do setor (Ibovespa) Peso do setor (Carteira) Companhia Ticker Peso Rating Preço-Alvo",
        "Commodities",
        "Energia 18,1% 17,5%",
        "Petrobras PETR4 12,5% Compra R\$ 63,00",
        "PRIO PRIO3 5,0% Compra R\$ 78,00",
        "Materiais 15,8% 12,5% Vale VALE3 12,5% Neutro R\$ 85,00",
        "Financeiro Financeiro 26,7% 5,0% BR Partners BRBI11 5,0% Compra R\$ 26,00",
        "B3 B3SA3 10,0% Neutro R\$ 16,00",
    )
    val page3Desempenho = arrayOf(
        "Companhia Ticker Peso Data de entrada Desempenho desde entrada Desempenho no mês Desempenho 2026",
        "Petrobras PETR4 10.00% jun-24 56.7% 6.9% 55.3%",
        "Vale VALE3 12.50% dez-23 34.0% 4.9% 11.2%",
        "Itaú Unibanco ITUB4 15.00% dez-22 128.9% -7.8% 2.8%",
    )

    "extracts every target, taking the weight before the rating and ignoring the sector weights" {
        val report = resolver.parse(pdfOf(*page1, *page3Desempenho))

        report.referenceDate shouldBe LocalDate(2026, 9, 1)
        report.targets shouldContainExactly listOf(
            StrategyTarget("PETR4", BigDecimal("0.1250"), "COMPRA", BigDecimal("63.00")),
            StrategyTarget("PRIO3", BigDecimal("0.0500"), "COMPRA", BigDecimal("78.00")),
            StrategyTarget("VALE3", BigDecimal("0.1250"), "NEUTRO", BigDecimal("85.00")),
            StrategyTarget("BRBI11", BigDecimal("0.0500"), "COMPRA", BigDecimal("26.00")),
            StrategyTarget("B3SA3", BigDecimal("0.1000"), "NEUTRO", BigDecimal("16.00")),
        )
    }

    "does not pick up the page-3 performance table, which has no rating" {
        val report = resolver.parse(pdfOf(*page1, *page3Desempenho))

        report.targets.count { it.ticker == "ITUB4" } shouldBe 0
        report.targets.first { it.ticker == "PETR4" }.weight shouldBe BigDecimal("0.1250")
    }

    "captures the changelog paragraph and stops at the date line" {
        val report = resolver.parse(pdfOf(*page1))

        report.changesText shouldBe
            "Estamos aumentando o peso de PETR4 de 10% para 12,5%, principalmente para elevar a exposição. " +
            "Estamos reduzindo o peso de ITUB4 de 15,0% para 10,0%, diante de um cenário mais desafiador."
    }
})
