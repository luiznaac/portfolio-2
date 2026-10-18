package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.strategy.InvalidStrategyReportException
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport
import org.springframework.stereotype.Component

// The last resort for an XP report whose layout is newer than the three known families: read every
// `TICKER peso%` pair, keeping a rating and target price when the line carries them. It does not
// enforce the 100% sum — an unrecognised layout may print weights differently — so its staged
// edition is always reviewed in the drawer before it is activated.
@Component
class XpGenericReportParser : IStrategyReportParser {

    override val name = "XpGeneric"
    override val order = 100

    override fun claims(pages: List<String>): Boolean =
        pages.any { page -> page.lineSequence().any(ROW::containsMatchIn) }

    override fun parse(fileName: String, pages: List<String>): ParsedStrategyReport {
        val entries = pages
            .flatMap { page -> page.lineSequence().mapNotNull { ROW.find(it)?.toEntry() }.toList() }
            .distinctBy { it.ticker }

        if (entries.isEmpty()) {
            throw InvalidStrategyReportException(fileName, "the portfolio table has no rows")
        }
        return ParsedStrategyReport(requireReferenceMonth(fileName, pages), name, entries)
    }

    private fun MatchResult.toEntry() = EditionEntry(
        ticker = groupValues[1].normalizedTicker(),
        weight = parsePtBrDecimal(groupValues[2]),
        rating = groupValues[3].takeIf { it.isNotBlank() }?.let(::parseRating),
        targetPrice = groupValues[4].takeIf { it.isNotBlank() }?.let(::parsePtBrDecimal),
    )

    private companion object {
        val ROW = Regex(
            "([A-Z][A-Z0-9]{3}\\d{1,2})\\s+(\\d+(?:,\\d+)?)%" +
                "(?:\\s+(Compra|Neutro|Venda))?(?:\\s+R\\$\\s*([\\d.,]+))?",
            RegexOption.IGNORE_CASE,
        )
    }
}
