package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.strategy.UnsupportedStrategyReportException
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport
import org.springframework.stereotype.Component

// The Top Ações / Top Dividendos / Top Small Caps layout: a nine-column table whose rows print
// `Companhia TICKER peso% Rating R$ preço-alvo`. Only the summary page's table is read — the
// performance page repeats the tickers with last month's dot-decimal weights and no rating, and
// must never feed targets (issue #93's stale-table trap).
@Component
class XpEquityCarteiraParser : IStrategyReportParser {

    override val name = "XpEquityCarteira"
    override val order = 10

    override fun claims(pages: List<String>): Boolean = pages.any(::claimsPage)

    override fun parse(fileName: String, pages: List<String>): ParsedStrategyReport {
        val page = pages.firstOrNull(::claimsPage) ?: throw UnsupportedStrategyReportException(fileName)
        val entries = page.lineSequence().mapNotNull { ROW.find(it)?.toEntry() }.toList()

        requireValidWeights(fileName, entries)
        return ParsedStrategyReport(requireReferenceMonth(fileName, pages), entries)
    }

    private fun claimsPage(page: String): Boolean =
        page.contains("Ticker") && page.contains("Peso") &&
            page.contains("Rating") && page.contains("Preço-Alvo")

    private fun MatchResult.toEntry() = EditionEntry(
        ticker = groupValues[1].normalizedTicker(),
        weight = parsePtBrDecimal(groupValues[2]),
        rating = parseRating(groupValues[3]),
        targetPrice = parsePtBrDecimal(groupValues[4]),
    )

    private companion object {
        val ROW = Regex(
            "([A-Z][A-Z0-9]{3}\\d{1,2})\\s+(\\d+(?:,\\d+)?)%\\s+(Compra|Neutro|Venda)\\s+R\\$\\s*([\\d.,]+)",
            RegexOption.IGNORE_CASE,
        )
    }
}
