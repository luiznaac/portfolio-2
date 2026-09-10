package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

class StrategyReportParserResolverTest : StringSpec({

    val onlyReport = ParsedStrategyReport(LocalDate(2030, 1, 1), null, listOf(StrategyTarget("AAAA3", BigDecimal.ONE)))

    fun parser(claims: Boolean) = object : StrategyReportParser {
        override fun shouldExecute(document: StrategyReportDocument) = claims
        override fun parse(document: StrategyReportDocument) = onlyReport
    }

    "should hand the document to the first parser that claims it" {
        val resolver = StrategyReportParserResolver(listOf(parser(claims = false), parser(claims = true)))

        resolver.parse(pdfOf("Carteira Top - Setembro/2026", "Petrobras PETR4 100,0%")) shouldBe onlyReport
    }

    "should fail loudly when no parser recognises the layout" {
        val resolver = StrategyReportParserResolver(listOf(parser(claims = false)))

        shouldThrow<StrategyReportParseException> { resolver.parse(pdfOf("something unrecognisable")) }
    }

    "should fail loudly when the upload isn't a PDF at all" {
        val resolver = StrategyReportParserResolver(listOf(XpStrategyReportParser()))

        shouldThrow<StrategyReportParseException> { resolver.parse("not a pdf".toByteArray()) }
    }

    "the fallback parser should not claim a document with no competência" {
        val document = StrategyReportDocument("Companhia Ticker Peso\nPetrobras PETR4 100,0%")

        XpStrategyReportParser().shouldExecute(document) shouldBe false
    }

    "the fallback parser should not claim a document with no target rows" {
        val document = StrategyReportDocument("Relatório de Desempenho - Setembro/2026\nRentabilidade acumulada")

        XpStrategyReportParser().shouldExecute(document) shouldBe false
    }
})

private fun pdfOf(vararg lines: String): ByteArray {
    val document = PDDocument()
    val page = PDPage()
    document.addPage(page)

    PDPageContentStream(document, page).use { stream ->
        stream.setFont(PDType1Font(Standard14Fonts.FontName.HELVETICA), 10f)
        stream.beginText()
        stream.newLineAtOffset(50f, 750f)
        lines.forEach { line ->
            stream.showText(line)
            stream.newLineAtOffset(0f, -14f)
        }
        stream.endText()
    }

    val output = ByteArrayOutputStream()
    document.save(output)
    document.close()
    return output.toByteArray()
}
