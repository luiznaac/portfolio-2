package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.WEIGHT_PCT
import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.dedupeByTicker
import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.percentToFraction
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import org.springframework.stereotype.Component

/**
 * XP "Estratégia Quantitativa" reports — Top Dividendos Plus. The table is
 * `Companhia (em ordem alfabética) Ticker Peso Setor` and each row is
 * `<Companhia> <Ticker> <peso>% <Setor em inglês>` — no rating, no target price.
 *
 * The report prints the same ticker list twice (page 1, then page 3 with an added Dividend Yield
 * column), so rows are de-duplicated by ticker, first occurrence winning.
 */
@Component
class XpQuantCarteiraReportParser : StrategyReportParser {

    override fun shouldExecute(document: StrategyReportDocument) =
        document.containsAllIgnoringCase("Estratégia Quantitativa", "Companhia", "Ticker", "Peso", "Setor") &&
            !document.containsAnyIgnoringCase("Rating", "Recomendação")

    override fun parse(document: StrategyReportDocument): ParsedStrategyReport {
        // Bounded to the page-1 table. The report repeats the ticker list on later pages — once
        // with a Dividend Yield column, once in a "Desempenho dos ativos" table that still lists
        // the two names dropped this month — so scanning every line would over-count.
        val targets = document
            .tableTickerLines { it.contains("Companhia") && it.contains("Ticker") && it.contains("Setor") }
            .mapNotNull { parseRow(document, it) }
            .dedupeByTicker()
        if (targets.isEmpty()) {
            throw StrategyReportParseException("XP quant report recognised but no target rows parsed")
        }

        return ParsedStrategyReport(
            referenceDate = document.referenceMonth()
                ?: throw StrategyReportParseException("No reference month in the XP quant report"),
            changesText = document.changelog(),
            targets = targets,
        )
    }

    private fun parseRow(document: StrategyReportDocument, line: String): StrategyTarget? {
        val ticker = document.findTicker(line)
        val weight = ticker?.let {
            WEIGHT_PCT.find(line, line.indexOf(it) + it.length)?.groupValues?.get(1)
        }

        return if (ticker != null && weight != null) {
            StrategyTarget(ticker = ticker, weight = percentToFraction(weight))
        } else {
            null
        }
    }
}
