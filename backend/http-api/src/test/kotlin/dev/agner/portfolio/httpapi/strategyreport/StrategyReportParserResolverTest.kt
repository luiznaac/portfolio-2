package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class StrategyReportParserResolverTest : StringSpec({

    val onlyReport = ParsedStrategyReport(LocalDate(2030, 1, 1), null, listOf(StrategyTarget("AAAA3", BigDecimal.ONE)))

    fun parser(claims: Boolean) = object : StrategyReportParser {
        override fun shouldExecute(document: StrategyReportDocument) = claims
        override fun parse(document: StrategyReportDocument) = onlyReport
    }

    "hands the document to the first parser that claims it" {
        val resolver = StrategyReportParserResolver(listOf(parser(claims = false), parser(claims = true)))

        resolver.parse(pdfOf("Carteira Setembro 2026", "Petrobras PETR4 100,0%")) shouldBe onlyReport
    }

    "fails loudly when no parser recognises the layout" {
        val resolver = StrategyReportParserResolver(listOf(parser(claims = false)))

        shouldThrow<StrategyReportParseException> { resolver.parse(pdfOf("something unrecognisable")) }
    }

    "fails loudly when the upload isn't a PDF at all" {
        val resolver = StrategyReportParserResolver(listOf(GenericLineReportParser()))

        shouldThrow<StrategyReportParseException> { resolver.parse("not a pdf".toByteArray()) }
    }
})
