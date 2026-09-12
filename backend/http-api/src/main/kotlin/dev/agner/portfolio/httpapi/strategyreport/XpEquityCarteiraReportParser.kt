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
 * XP Equity Research "Carteira XP" reports — Top Ações, Top Dividendos, Top Small Caps. The
 * composition table header is
 * `Segmento Setor Peso do setor (Ibovespa) Peso do setor (Carteira) Companhia Ticker Peso Rating Preço-Alvo`,
 * and each target row ends `… <Ticker> <peso>% <Compra|Neutro|Venda> R$ <preço-alvo>`.
 *
 * Two traps this layout sets, both handled here:
 * - a row can carry the sector's Ibovespa and carteira weights *before* the ticker
 *   (`Materiais 15,8% 12,5% Vale VALE3 12,5% Neutro R$ 85,00`), so the weight is the last `%`
 *   *before the rating word*, not the first `%` on the line;
 * - page 3 repeats every ticker in a "Desempenho de cada ativo" table with entry-date weights and
 *   no rating — only rows that carry a rating are treated as targets, which excludes it.
 */
@Component
class XpEquityCarteiraReportParser : StrategyReportParser {

    override fun shouldExecute(document: StrategyReportDocument) =
        document.containsAllIgnoringCase("EQUITY RESEARCH", "Companhia", "Ticker", "Rating") &&
            document.containsAnyIgnoringCase("Preço-Alvo", "Preco-Alvo")

    override fun parse(document: StrategyReportDocument): ParsedStrategyReport {
        val targets = document.lines.mapNotNull { parseRow(document, it) }.dedupeByTicker()
        if (targets.isEmpty()) {
            throw StrategyReportParseException("XP equity report recognised but no target rows parsed")
        }

        return ParsedStrategyReport(
            referenceDate = document.referenceMonth()
                ?: throw StrategyReportParseException("No reference month in the XP equity report"),
            changesText = document.changelog(),
            targets = targets,
        )
    }

    private fun parseRow(document: StrategyReportDocument, line: String): StrategyTarget? {
        val rating = RATING.find(line) ?: return null
        val ticker = document.findTicker(line) ?: return null

        val beforeRating = line.substring(0, rating.range.first)
        val weight = WEIGHT_PCT.findAll(beforeRating).lastOrNull()?.groupValues?.get(1) ?: return null

        return StrategyTarget(
            ticker = ticker,
            weight = percentToFraction(weight),
            rating = rating.value.uppercase(),
            targetPrice = TARGET_PRICE.find(line, rating.range.last)?.groupValues?.get(1)?.let(::brDecimal),
        )
    }
}
