package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component

/**
 * Extracts the PDF's text once (pdfbox — same dependency and technique as
 * [dev.agner.portfolio.httpapi.configuration.PdfConverter]) and hands it to the first registered
 * [StrategyReportParser] that claims it. Spring injects every `@Component` implementation, so a
 * new layout is a new class and no edit here.
 */
@Component
class StrategyReportParserResolver(
    parsers: List<StrategyReportParser>,
) {

    private val parsers = parsers.sortedBy { it.precedence }

    fun parse(pdfBytes: ByteArray): ParsedStrategyReport {
        val document = StrategyReportDocument(extractText(pdfBytes))

        val parser = this.parsers.firstOrNull { it.shouldExecute(document) }
            ?: throw StrategyReportParseException(
                "No parser recognises this report's layout. Tried: $parserNames",
            )

        return parser.parse(document)
    }

    private val parserNames get() = parsers.joinToString { it.javaClass.simpleName }

    private fun extractText(pdfBytes: ByteArray): String =
        try {
            Loader.loadPDF(pdfBytes).use { PDFTextStripper().getText(it) }
        } catch (e: Exception) {
            throw StrategyReportParseException("Could not read the uploaded file as a PDF: ${e.message}")
        }
}
