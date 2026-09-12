package dev.agner.portfolio.httpapi.strategyreport

import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.RATING
import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.WEIGHT_PCT
import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.dedupeByTicker
import dev.agner.portfolio.httpapi.strategyreport.StrategyReportDocument.Companion.percentToFraction
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import org.springframework.stereotype.Component

/**
 * XP "Research FIIs" — the Carteira Fundamentalista de Fundos Imobiliários. Table header is
 * `Por Ticker Segmento Ticker Recomendação Nome …`, and each row starts with the weight:
 * `<peso>% <Segmento> <Ticker> COMPRA <Nome do fundo> <VM> <cota> <VP> <VM/VP>% …`.
 *
 * The weight is the first `%` on the line (the later `102%`, `1,9%` … are performance columns).
 * There is no target price for a fund; the recommendation is always COMPRA.
 */
@Component
class XpFiiCarteiraReportParser : StrategyReportParser {

    override fun shouldExecute(document: StrategyReportDocument) =
        document.containsAnyIgnoringCase("Research FIIs", "Fundos Imobiliários") &&
            document.containsAllIgnoringCase("Carteira Fundamentalista", "Por Ticker", "Recomendação")

    override fun parse(document: StrategyReportDocument): ParsedStrategyReport {
        // Bounded to the composition table. The report is 40 pages: without a boundary, a fund
        // ticker mentioned in a later deep-dive ("venda de R$ 130 mil em cotas de CYCR11") would
        // be read as a target.
        val targets = document.tableTickerLines { it.contains("Por Ticker") && it.contains("Recomendação") }
            .mapNotNull { parseRow(document, it) }
            .dedupeByTicker()
        if (targets.isEmpty()) {
            throw StrategyReportParseException("XP FII report recognised but no target rows parsed")
        }

        return ParsedStrategyReport(
            referenceDate = document.referenceMonth()
                ?: throw StrategyReportParseException("No reference month in the XP FII report"),
            changesText = document.changelog(),
            targets = targets,
        )
    }

    private fun parseRow(document: StrategyReportDocument, line: String): StrategyTarget? {
        val ticker = document.findTicker(line) ?: return null
        val recommendation = RATING.find(line) ?: return null

        // The weight sits before the ticker; the first % on the line is it.
        val weight = WEIGHT_PCT.find(line)?.takeIf { it.range.first < line.indexOf(ticker) }
            ?.groupValues?.get(1) ?: return null

        return StrategyTarget(
            ticker = ticker,
            weight = percentToFraction(weight),
            rating = recommendation.value.uppercase(),
        )
    }
}
