package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.RATING
import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.TARGET_PRICE
import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.WEIGHT_PCT
import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.brDecimal
import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.dedupeByTicker
import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.percentToFraction
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import org.springframework.stereotype.Component

/**
 * The last-resort parser for a report no specific parser recognises. It treats any line carrying a
 * B3 ticker and a `%` weight as a target row, taking the `%` nearest after the ticker (or, failing
 * that, the last `%` before it). Rows are de-duplicated by ticker.
 *
 * It cannot know a new broker's quirks — a performance table, a sector-weight column — so a report
 * that lands here is worth turning into its own parser. Tried last.
 */
@Component
class GenericLineReportParser : StrategyReportParser {

    override val precedence: Int get() = Int.MAX_VALUE

    override fun shouldExecute(document: StrategyReportDocument) =
        document.referenceMonth() != null && document.lines.any { targetRow(document, it) != null }

    override fun parse(document: StrategyReportDocument): ParsedStrategyReport {
        val targets = document.lines.mapNotNull { targetRow(document, it) }.dedupeByTicker()
        if (targets.isEmpty()) {
            throw StrategyReportParseException("No target rows found in the report")
        }

        return ParsedStrategyReport(
            referenceDate = document.referenceMonth()
                ?: throw StrategyReportParseException("Could not find a reference month in the report"),
            changesText = document.changelog(),
            targets = targets,
        )
    }

    private fun targetRow(document: StrategyReportDocument, line: String): StrategyTarget? {
        val ticker = document.findTicker(line)
        val weight = ticker?.let {
            val tickerEnd = line.indexOf(it) + it.length
            WEIGHT_PCT.find(line, tickerEnd)?.groupValues?.get(1)
                ?: WEIGHT_PCT.findAll(line.take(tickerEnd)).lastOrNull()?.groupValues?.get(1)
        }

        return if (ticker != null && weight != null) {
            StrategyTarget(
                ticker = ticker,
                weight = percentToFraction(weight),
                rating = RATING.find(line)?.value?.uppercase(),
                targetPrice = TARGET_PRICE.find(line)?.groupValues?.get(1)?.let(::brDecimal),
            )
        } else {
            null
        }
    }
}
