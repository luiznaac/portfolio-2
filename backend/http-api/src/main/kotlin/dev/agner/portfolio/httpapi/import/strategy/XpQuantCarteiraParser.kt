package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.strategy.UnsupportedStrategyReportException
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport
import org.springframework.stereotype.Component

// The Top Dividendos Plus (quantitative) layout: `Companhia TICKER peso% Setor`, equal weights and
// no rating or target price. The page is recognised by having a ticker/weight table without the
// equity layout's Rating column.
@Component
class XpQuantCarteiraParser : IStrategyReportParser {

    override val name = "XpQuantCarteira"
    override val order = 20

    override fun claims(pages: List<String>): Boolean = pages.any(::claimsPage)

    override fun parse(fileName: String, pages: List<String>): ParsedStrategyReport {
        val page = pages.firstOrNull(::claimsPage) ?: throw UnsupportedStrategyReportException(fileName)
        val entries = page.lineSequence().mapNotNull { ROW.find(it)?.toEntry() }.toList()

        requireValidWeights(fileName, entries)
        return ParsedStrategyReport(requireReferenceMonth(fileName, pages), entries)
    }

    private fun claimsPage(page: String): Boolean =
        page.contains("Ticker") && page.contains("Peso") &&
            page.contains("Setor") && !page.contains("Rating") && !page.contains("Recomenda")

    private fun MatchResult.toEntry() = EditionEntry(
        ticker = groupValues[1].normalizedTicker(),
        weight = parsePtBrDecimal(groupValues[2]),
    )

    private companion object {
        val ROW = Regex(
            "([A-Z][A-Z0-9]{3}\\d{1,2})\\s+(\\d+(?:,\\d+)?)%\\s+\\S",
            RegexOption.IGNORE_CASE,
        )
    }
}
