package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.strategy.UnsupportedStrategyReportException
import dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport
import org.springframework.stereotype.Component

// Picks the layout parser for an uploaded strategy report: extract the page text once, then let the
// first parser that claims it win. Parsers are ordered so the three known XP families are tried
// before the generic fallback; a file no parser claims is rejected loudly rather than staged empty.
@Component
class StrategyReportParserResolver(
    private val parsers: List<IStrategyReportParser>,
) {

    fun resolve(fileName: String, content: ByteArray): ParsedStrategyReport =
        resolvePages(fileName, pdfPages(fileName, content))

    fun resolvePages(fileName: String, pages: List<String>): ParsedStrategyReport =
        parsers.sortedBy { it.order }
            .firstOrNull { it.claims(pages) }
            ?.parse(fileName, pages)
            ?: throw UnsupportedStrategyReportException(fileName)
}
