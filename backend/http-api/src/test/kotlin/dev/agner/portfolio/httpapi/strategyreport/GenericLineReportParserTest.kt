package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * The last-resort parser: any line with a B3 ticker and a `%` weight is a target row. It exists so
 * an unrecognised broker's report still produces something; a report that lands here is a signal
 * to write a dedicated parser.
 */
class GenericLineReportParserTest : StringSpec({

    val resolver = StrategyReportParserResolver(listOf(GenericLineReportParser()))

    "extracts ticker, weight, rating and target price from a plain table" {
        val report = resolver.parse(
            pdfOf(
                "Carteira Genérica Setembro 2026",
                "Companhia Ticker Peso Rating Preço-Alvo",
                "Petrobras PETR4 60,0% Compra R\$ 45,00",
                "Vale VALE3 40,0% Neutro R\$ 70,50",
            ),
        )

        report.referenceDate shouldBe LocalDate(2026, 9, 1)
        report.targets shouldBe listOf(
            StrategyTarget("PETR4", BigDecimal("0.6000"), "COMPRA", BigDecimal("45.00")),
            StrategyTarget("VALE3", BigDecimal("0.4000"), "NEUTRO", BigDecimal("70.50")),
        )
    }

    "takes the weight after the ticker when the row leads with other percentages" {
        val report = resolver.parse(
            pdfOf("Setembro 2026", "Setor 18,1% Vale VALE3 12,5% Neutro"),
        )

        report.targets.single().weight shouldBe BigDecimal("0.1250")
    }

    "de-duplicates a ticker that appears on more than one line" {
        val report = resolver.parse(
            pdfOf("Setembro 2026", "Petrobras PETR4 60,0%", "PETR4 aparece de novo com 99,0%"),
        )

        report.targets.single().ticker shouldBe "PETR4"
    }

    "does not claim a document with no reference month" {
        GenericLineReportParser().shouldExecute(
            StrategyReportDocument("Companhia Ticker Peso\nPetrobras PETR4 100,0%"),
        ) shouldBe false
    }

    "does not claim a document with no target rows" {
        GenericLineReportParser().shouldExecute(
            StrategyReportDocument("Relatório de Desempenho Setembro 2026\nRentabilidade acumulada"),
        ) shouldBe false
    }

    "the resolver fails loudly when nothing recognises the layout" {
        shouldThrow<StrategyReportParseException> {
            StrategyReportParserResolver(emptyList()).parse(pdfOf("Setembro 2026", "PETR4 10,0%"))
        }
    }

    "the resolver fails loudly when the upload isn't a PDF" {
        shouldThrow<StrategyReportParseException> { resolver.parse("not a pdf".toByteArray()) }
    }
})
