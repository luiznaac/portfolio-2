package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.IStrategyReportParser
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import kotlinx.datetime.LocalDate
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import org.springframework.stereotype.Component
import java.math.BigDecimal

/**
 * Extracts an XP model-portfolio PDF's text (pdfbox — same dependency and technique as
 * [PdfConverter]) and regexes the target table out of it. Line-based, not position-based: the XP
 * report's real column layout (page 1 for stocks, page 2 for FIIs — see the plan's "Fases" §2)
 * has never been run through this — there was no sample PDF available when this was written, only
 * the plan's own description of the columns. Treat the regexes below as a documented best guess
 * to correct against the first real report, not a verified implementation.
 */
@Component
class PdfBoxStrategyReportParser : IStrategyReportParser {

    override fun parse(pdfBytes: ByteArray): ParsedStrategyReport {
        val text = PDFTextStripper().getText(Loader.loadPDF(pdfBytes))
        val lines = text.lines()

        return ParsedStrategyReport(
            referenceDate = extractReferenceDate(text)
                ?: throw StrategyReportParseException("Could not find a competência (month/year) in the report"),
            changesText = extractChangesText(lines),
            targets = extractTargets(lines),
        )
    }

    private fun extractTargets(lines: List<String>): List<StrategyTarget> =
        lines.mapNotNull { line ->
            val ticker = findTicker(line) ?: return@mapNotNull null
            val weightPct = WEIGHT.find(line)?.groupValues?.get(1)?.toBrDecimal() ?: return@mapNotNull null

            StrategyTarget(
                ticker = ticker,
                weight = weightPct.divide(BigDecimal(100), 4, java.math.RoundingMode.HALF_EVEN),
                rating = RATING.find(line)?.value,
                targetPrice = TARGET_PRICE.find(line)?.groupValues?.get(1)?.toBrDecimal(),
            )
        }

    // "Estamos adicionando ORVR3... removendo CEAB3..." — the changelog paragraph the plan calls
    // out as worth keeping verbatim. Collected as every consecutive non-blank line starting from
    // the one that opens with "Estamos".
    private fun extractChangesText(lines: List<String>): String? {
        val start = lines.indexOfFirst { it.trimStart().startsWith("Estamos", ignoreCase = true) }
        if (start < 0) return null

        return lines.drop(start)
            .takeWhile { it.isNotBlank() }
            .joinToString(" ") { it.trim() }
            .trim()
            .ifBlank { null }
    }

    private fun extractReferenceDate(text: String): LocalDate? {
        val match = COMPETENCIA.find(text) ?: return null
        val month = MONTHS[match.groupValues[1].lowercase().take(3)] ?: return null
        val year = match.groupValues[2].let { if (it.length == 2) "20$it" else it }.toInt()
        return LocalDate(year, month, 1)
    }

    private fun String.toBrDecimal() = BigDecimal(replace(".", "").replace(",", "."))

    // B3's ticker root is 4 alphanumeric characters — not always pure letters (the exchange's
    // own ticker is "B3SA4"/"B3SA3") — followed by a 1-2 digit class suffix. Matching
    // [A-Z0-9]{4} alone would also catch plain numbers (a price, a percentage), so a candidate
    // only counts if its root has at least one actual letter.
    private fun findTicker(line: String): String? =
        TICKER_CANDIDATE.findAll(line)
            .map { it.value }
            .firstOrNull { candidate -> candidate.take(4).any(Char::isLetter) }

    private companion object {
        val TICKER_CANDIDATE = Regex("""\b[A-Z0-9]{4}\d{1,2}\b""")
        val WEIGHT = Regex("""(\d{1,3}(?:,\d+)?)\s*%""")
        val TARGET_PRICE = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})""")
        val RATING = Regex("""\b(COMPRA|NEUTRO|VENDA)\b""")
        val COMPETENCIA = Regex(
            """(?i)\b(janeiro|fevereiro|março|marco|abril|maio|junho|julho""" +
                """|agosto|setembro|outubro|novembro|dezembro)[/\s]+(\d{2}|\d{4})\b""",
        )
        val MONTHS = mapOf(
            "jan" to 1, "fev" to 2, "mar" to 3, "abr" to 4, "mai" to 5, "jun" to 6,
            "jul" to 7, "ago" to 8, "set" to 9, "out" to 10, "nov" to 11, "dez" to 12,
        )
    }
}
