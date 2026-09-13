package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.math.RoundingMode

/**
 * One broker's model-portfolio report layout.
 *
 * Layouts vary by broker and, within one broker, by desk — XP alone publishes at least three
 * shapes (equity "Carteira XP", "Estratégia Quantitativa", "Research FIIs"), each built by a
 * different team. Format detection is therefore each parser's own job: [StrategyReportParserResolver]
 * extracts the PDF's text once and asks each registered parser whether it recognises it. Adding
 * support for a new layout is adding a `@Component` that implements this interface.
 */
interface StrategyReportParser {

    /** Lower is tried first. A purpose-built parser leaves this at 0; a permissive fallback raises it. */
    val precedence: Int get() = 0

    /** Whether this parser recognises [document] as its own layout. Must not throw. */
    fun shouldExecute(document: StrategyReportDocument): Boolean

    /**
     * Parses a document [shouldExecute] returned true for, or throws
     * [dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException].
     */
    fun parse(document: StrategyReportDocument): ParsedStrategyReport
}

/**
 * A report's extracted text, plus the parsing primitives every layout needs. Built once by the
 * resolver so each candidate parser inspects exactly the same thing.
 */
data class StrategyReportDocument(val text: String) {

    val lines: List<String> = text.lines()

    fun containsAllIgnoringCase(vararg needles: String) = needles.all { text.contains(it, ignoreCase = true) }

    fun containsAnyIgnoringCase(vararg needles: String) = needles.any { text.contains(it, ignoreCase = true) }

    /** The first line matching [predicate], or null. */
    fun lineMatching(predicate: (String) -> Boolean): String? = lines.firstOrNull(predicate)

    /**
     * The reference month (Portuguese "competência"). Every XP report carries it as a standalone
     * line ("Setembro 2026"), which is preferred; a month-year embedded in a sentence (a
     * performance history — "no período entre outubro de 2020 …") is only the last resort.
     */
    fun referenceMonth(): LocalDate? =
        lines.firstNotNullOfOrNull { line -> MONTH_YEAR.matchEntire(line.trim())?.let(::monthYearToDate) }
            ?: MONTH_YEAR.find(text)?.let(::monthYearToDate)

    /**
     * Data lines of the composition table: from the line matching [isHeader], the run of following
     * lines that each carry a ticker. Assumes the rows are contiguous (no sector name broken onto
     * its own line mid-table) — true for the layouts that use this; the equity layout scans instead.
     */
    fun tableTickerLines(isHeader: (String) -> Boolean): List<String> {
        val start = lines.indexOfFirst(isHeader)
        if (start < 0) return emptyList()

        return lines.asSequence()
            .drop(start + 1)
            .dropWhile { findTicker(it) == null }
            .takeWhile { findTicker(it) != null }
            .toList()
    }

    /**
     * The changelog paragraph, from the first line opening with a change verb ("Estamos ...",
     * "Alterações: ...", "Mudanças ...") up to the next structural boundary. Best-effort: the XP
     * reports don't separate it with a blank line, so an adjacent sentence occasionally rides along.
     */
    fun changelog(): String? {
        val start = lines.indexOfFirst { CHANGELOG_ANCHOR.containsMatchIn(it.trimStart()) }
        if (start < 0) return null

        return lines.asSequence()
            .drop(start)
            .takeWhile { it.isNotBlank() && !isChangelogBoundary(it.trim()) }
            .take(MAX_CHANGELOG_LINES)
            .joinToString(" ") { it.trim() }
            .trim()
            .ifBlank { null }
    }

    /**
     * The first B3 ticker candidate on [line] — four alphanumerics (the exchange's own ticker is
     * "B3SA3", so not always letters) plus a 1–2 digit class code, with at least one letter so a
     * bare number can't pass.
     */
    fun findTicker(line: String): String? =
        TICKER.findAll(line)
            .map { it.value }
            .firstOrNull { it.take(4).any(Char::isLetter) }

    private fun monthYearToDate(match: MatchResult): LocalDate? {
        val month = MONTHS[match.groupValues[1].lowercase().take(3)] ?: return null
        val year = match.groupValues[2].let { if (it.length == 2) "20$it" else it }.toInt()
        return LocalDate(year, month, 1)
    }

    private fun isChangelogBoundary(line: String): Boolean =
        line.startsWith("Fonte:", ignoreCase = true) ||
            line.startsWith("Fontes:", ignoreCase = true) ||
            line.startsWith("Figura", ignoreCase = true) ||
            DATE_LINE.matches(line) ||
            MONTH_YEAR.matches(line) ||
            TABLE_HEADER.containsMatchIn(line) ||
            // the next section of every XP report opens with one of these
            SECTION_OPENER.containsMatchIn(line)

    companion object {
        private val MONTHS = mapOf(
            "jan" to 1, "fev" to 2, "mar" to 3, "abr" to 4, "mai" to 5, "jun" to 6,
            "jul" to 7, "ago" to 8, "set" to 9, "out" to 10, "nov" to 11, "dez" to 12,
        )
        private val MONTH_YEAR = Regex(
            """(?i)\b(janeiro|fevereiro|março|marco|abril|maio|junho|julho|agosto""" +
                """|setembro|outubro|novembro|dezembro)\s+(?:de\s+)?(\d{2}|\d{4})\b""",
        )
        private val DATE_LINE = Regex("""\d{1,2}º?\s+de\s+\p{L}+\s+de\s+\d{4}""")
        private val CHANGELOG_ANCHOR = Regex("""(?i)^(estamos|altera[çc][õo]es|mudan[çc]as)\b""")
        private val TABLE_HEADER = Regex(
            """(?i)\bcompanhia\b.*\bticker\b|\bpor ticker\b.*\bsegmento\b|\bpeso\s*%\s+fundo\b""",
        )
        private val SECTION_OPENER = Regex(
            """(?i)^(esta|nesta|nessa) carteira\b|^para quem\b|^que tipos\b|^empresas com\b""" +
                """|^(performance|desempenho|objetivo|coment[áa]rio)\s*[.:]|^mudan[çc]as na carteira\b""",
        )
        private const val MAX_CHANGELOG_LINES = 10

        val TICKER: Regex = Regex("""\b[A-Z0-9]{4}\d{1,2}\b""")
        val WEIGHT_PCT: Regex = Regex("""(\d{1,3}(?:,\d+)?)\s*%""")
        val RATING: Regex = Regex("""(?i)\b(compra|neutro|venda)\b""")
        val TARGET_PRICE: Regex = Regex("""R\$\s*(\d{1,3}(?:\.\d{3})*,\d{2})""")

        private val ONE_HUNDRED = BigDecimal(100)

        /** "12,5" (Brazilian) → 0.1250; the fraction convention the domain stores. */
        fun percentToFraction(brazilianNumber: String): BigDecimal =
            brDecimal(brazilianNumber).divide(ONE_HUNDRED, 4, RoundingMode.HALF_EVEN)

        /** "1.234,56" (Brazilian) → 1234.56. */
        fun brDecimal(brazilianNumber: String): BigDecimal =
            BigDecimal(brazilianNumber.replace(".", "").replace(",", "."))

        /** Keeps the first target seen per ticker — some reports repeat the table with extra columns. */
        fun List<StrategyTarget>.dedupeByTicker(): List<StrategyTarget> =
            associateBy { it.ticker }.values.toList()
    }
}
