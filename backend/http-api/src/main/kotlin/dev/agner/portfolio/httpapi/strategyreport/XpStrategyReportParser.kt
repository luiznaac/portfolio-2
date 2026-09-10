package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import kotlinx.datetime.LocalDate
import org.springframework.core.annotation.Order
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The line-based layout: a table whose rows each carry a ticker, a percentage weight and
 * optionally a rating and a target price, plus a reference month somewhere in the text and an
 * "Estamos ..." changelog paragraph. The Portuguese literals below match the report's own text.
 *
 * Line-based, not position-based, and never verified against a real report — there was no sample
 * PDF available when this was written, only a description of the columns. Treat the regexes as a
 * documented best guess to correct against the first real report.
 *
 * Ordered last so a parser written against a real, specific layout is always tried first; this one
 * is the permissive fallback.
 */
@Component
@Order(FALLBACK_ORDER)
class XpStrategyReportParser : StrategyReportParser {

    // Permissive on purpose: anything with a reference month and at least one ticker+weight row is
    // worth attempting. A stricter parser registered ahead of this one wins when it matches.
    override fun shouldExecute(document: StrategyReportDocument): Boolean =
        REFERENCE_MONTH.containsMatchIn(document.text) && document.lines.any { hasTargetRow(it) }

    override fun parse(document: StrategyReportDocument): ParsedStrategyReport =
        ParsedStrategyReport(
            referenceDate = extractReferenceDate(document.text)
                ?: throw StrategyReportParseException("Could not find a reference month in the report"),
            changesText = extractChangesText(document.lines),
            targets = extractTargets(document.lines),
        )

    private fun hasTargetRow(line: String) = findTicker(line) != null && WEIGHT.containsMatchIn(line)

    private fun extractTargets(lines: List<String>): List<StrategyTarget> =
        lines.mapNotNull { line ->
            val ticker = findTicker(line) ?: return@mapNotNull null
            val weightPct = WEIGHT.find(line)?.groupValues?.get(1)?.toBrDecimal() ?: return@mapNotNull null

            StrategyTarget(
                ticker = ticker,
                weight = weightPct.divide(ONE_HUNDRED, 4, RoundingMode.HALF_EVEN),
                rating = RATING.find(line)?.value,
                targetPrice = TARGET_PRICE.find(line)?.groupValues?.get(1)?.toBrDecimal(),
            )
        }

    // Every consecutive non-blank line from the one opening with "Estamos" — the changelog
    // paragraph ("Estamos adicionando ORVR3... removendo CEAB3..."), kept verbatim.
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
        val match = REFERENCE_MONTH.find(text) ?: return null
        val month = MONTHS[match.groupValues[1].lowercase().take(3)] ?: return null
        val year = match.groupValues[2].let { if (it.length == 2) "20$it" else it }.toInt()
        return LocalDate(year, month, 1)
    }

    private fun String.toBrDecimal() = BigDecimal(replace(".", "").replace(",", "."))

    // B3's ticker root is 4 alphanumeric characters — not always pure letters (the exchange's own
    // ticker is "B3SA4"/"B3SA3") — followed by a 1-2 digit class suffix. Matching [A-Z0-9]{4}
    // alone would also catch plain numbers (a price, a percentage), so a candidate only counts if
    // its root has at least one actual letter.
    private fun findTicker(line: String): String? =
        TICKER_CANDIDATE.findAll(line)
            .map { it.value }
            .firstOrNull { candidate -> candidate.take(4).any(Char::isLetter) }

    private companion object {
        val ONE_HUNDRED: BigDecimal = BigDecimal(100)
        val TICKER_CANDIDATE = Regex("""\b[A-Z0-9]{4}\d{1,2}\b""")
        val WEIGHT = Regex("""(\d{1,3}(?:,\d+)?)\s*%""")
        val TARGET_PRICE = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})""")
        val RATING = Regex("""\b(COMPRA|NEUTRO|VENDA)\b""")
        val REFERENCE_MONTH = Regex(
            """(?i)\b(janeiro|fevereiro|março|marco|abril|maio|junho|julho""" +
                """|agosto|setembro|outubro|novembro|dezembro)[/\s]+(\d{2}|\d{4})\b""",
        )
        val MONTHS = mapOf(
            "jan" to 1, "fev" to 2, "mar" to 3, "abr" to 4, "mai" to 5, "jun" to 6,
            "jul" to 7, "ago" to 8, "set" to 9, "out" to 10, "nov" to 11, "dez" to 12,
        )
    }
}

/** Lowest precedence — see [XpStrategyReportParser]. */
const val FALLBACK_ORDER = Int.MAX_VALUE
