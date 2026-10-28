package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.strategy.UnsupportedStrategyReportException
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport

// The shared shape of the three known XP layouts: a table of `TICKER peso% …` rows anchored on a
// header line inside the extracted page text. A concrete parser names its header predicate, its row
// regex and how a matched row becomes an entry; the base reads only the first page that claims the
// layout, then runs the shared weight check and reference-month read.
abstract class XpTableReportParser : IStrategyReportParser {

    override fun claims(pages: List<String>): Boolean = pages.any(::claimsPage)

    override fun parse(fileName: String, pages: List<String>): ParsedStrategyReport {
        val page = pages.firstOrNull(::claimsPage) ?: throw UnsupportedStrategyReportException(fileName)
        val entries = page.lineSequence().mapNotNull { row.find(it)?.let(::toEntry) }.toList()

        requireValidWeights(fileName, entries)
        return ParsedStrategyReport(requireReferenceMonth(fileName, pages), name, entries)
    }

    // The header line that identifies the layout inside an already-extracted page.
    protected abstract fun claimsPage(page: String): Boolean

    protected abstract val row: Regex

    protected abstract fun toEntry(match: MatchResult): EditionEntry
}
