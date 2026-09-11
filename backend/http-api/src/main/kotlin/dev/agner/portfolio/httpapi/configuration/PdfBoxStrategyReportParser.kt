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
 * Extracts a broker model-portfolio PDF's text (pdfbox — same dependency and technique as
 * [PdfConverter]) and regexes the target table out of it. Line-based, not position-based: the
 * report's real column layout (page 1 for stocks, page 2 for FIIs — see the plan's "Fases" §2)
 * has never been run through this — there was no sample PDF available when this was written, only
 * the plan's own description of the columns. Treat the regexes below as a documented best guess
 * to correct against the first real report, not a verified implementation.
 */
@Component
class PdfBoxStrategyReportParser : IStrategyReportParser {

    override fun parse(pdfBytes: ByteArray): ParsedStrategyReport =
        Loader.loadPDF(pdfBytes).use { document ->
            val text = PDFTextStripper().getText(document)
            val lines = text.lines()

            val portfolioLines = portfolioSectionLines(lines)
            if (portfolioLines.isEmpty()) {
                throw StrategyReportParseException("Could not find a portfolio table in the report")
            }

            ParsedStrategyReport(
                referenceDate = extractReferenceDate(text)
                    ?: throw StrategyReportParseException("Could not find a competência (month/year) in the report"),
                changesText = extractChangesText(lines),
                targets = extractTargets(portfolioLines),
            )
        }

    // The report carries more than one numeric table: the stock and FII portfolio tables, and the
    // page-3 "Desempenho" performance table, whose rows can also carry tickers and weights. Anchor
    // extraction to the portfolio table headers so a performance row can never be read as a
    // target (the previous implementation matched a ticker anywhere in the whole text).
    private fun portfolioSectionLines(lines: List<String>): List<String> {
        val section = mutableListOf<String>()
        var insideSection = false

        lines.forEach { line ->
            when {
                isPortfolioHeader(line) -> {
                    insideSection = true
                    section += line
                }
                insideSection && isSectionBoundary(line) -> insideSection = false
                insideSection -> section += line
            }
        }

        return section
    }

    private fun isPortfolioHeader(line: String): Boolean {
        val normalized = line.lowercase()
        return normalized.contains("ticker") && normalized.contains("peso")
    }

    private fun isSectionBoundary(line: String): Boolean =
        line.isBlank() || SECTION_END_MARKERS.any { line.contains(it, ignoreCase = true) }

    // Identifying a target line and reading its weight are separate concerns: a line that carries
    // a ticker but no readable weight is a parse failure worth reporting, not a line to silently
    // drop (which used to surface to the user as a generic "No targets found in report").
    private fun extractTargets(lines: List<String>): List<StrategyTarget> {
        val targets = mutableListOf<StrategyTarget>()
        val failures = mutableListOf<String>()
        var weightColumn: Int? = null

        lines.forEachIndexed { index, line ->
            if (isPortfolioHeader(line)) {
                weightColumn = line.split(COLUMN_SEPARATOR)
                    .map(String::trim)
                    .filter(String::isNotEmpty)
                    .indexOfFirst { it.contains("peso", ignoreCase = true) }
                    .takeIf { it >= 0 }
                return@forEachIndexed
            }

            val ticker = findTicker(line) ?: return@forEachIndexed
            val weightPct = extractWeightPct(line, weightColumn)
            if (weightPct == null) {
                failures += "line ${index + 1} (\"${line.trim()}\"): could not read a weight for $ticker"
                return@forEachIndexed
            }

            targets += StrategyTarget(
                ticker = ticker,
                weight = weightPct.divide(BigDecimal(100), 4, java.math.RoundingMode.HALF_EVEN),
                rating = RATING.find(line)?.value,
                targetPrice = TARGET_PRICE.find(line)?.groupValues?.get(1)?.toBrDecimal(),
            )
        }

        if (failures.isNotEmpty()) {
            throw StrategyReportParseException("Could not read target weights: ${failures.joinToString("; ")}")
        }

        return targets
    }

    // The weight usually carries the "%" suffix, but not always: the stock and FII tables order
    // their columns differently and some rows print the value bare. When the suffix is missing,
    // fall back to the column the current table header labels as "peso".
    private fun extractWeightPct(line: String, weightColumn: Int?): BigDecimal? {
        WEIGHT.find(line)?.groupValues?.get(1)?.let { return it.toBrDecimal() }

        val cell = weightColumn
            ?.let { line.split(COLUMN_SEPARATOR).map(String::trim).filter(String::isNotEmpty).getOrNull(it) }
            ?: return null

        return BARE_DECIMAL.find(cell)?.groupValues?.get(1)?.toBrDecimal()
    }

    // "Estamos adicionando ORVR3... removendo CEAB3..." — the changelog paragraph the plan calls
    // out as worth keeping verbatim. Collected as every consecutive non-blank line starting from
    // the one that opens with "Estamos".
    private fun extractChangesText(lines: List<String>): String? {
        val start = lines.indexOfFirst { it.trimStart().startsWith("Estamos", ignoreCase = true) }
        if (start < 0) return null

        return lines.drop(start)
            .takeWhile { line ->
                line.isNotBlank() && !TABLE_HEADER_MARKERS.any { marker ->
                    line.contains(marker, ignoreCase = true)
                }
            }
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
        val BARE_DECIMAL = Regex("""(\d{1,3}(?:,\d+)?)""")
        val COLUMN_SEPARATOR = Regex("""\s{2,}""")
        val TARGET_PRICE = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})""")
        val RATING = Regex("""\b(COMPRA|NEUTRO|VENDA)\b""")

        // Stops the target section at the changelog paragraph and the page-3 performance table.
        val SECTION_END_MARKERS = listOf("desempenho", "estamos")

        val TABLE_HEADER_MARKERS = listOf("desempenho")
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
