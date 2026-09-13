package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.strategy.model.StrategyEditionCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyEditionWithDiff
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import dev.agner.portfolio.usecase.strategy.repository.IStrategyEditionRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Turns an already-parsed broker model-portfolio report into a new, immutable
 * [dev.agner.portfolio.usecase.strategy.model.StrategyEdition] and exposes the diff against the
 * previous one. Extracting the report from whatever file the broker sent is an adapter concern
 * and stays in `http-api`; this service only ever sees [ParsedStrategyReport].
 *
 * Hard validation before saving — a bad parse (the wrong table, a truncated one) fails loudly
 * instead of silently corrupting a strategy's weights.
 */
@Service
class StrategyEditionService(
    private val repository: IStrategyEditionRepository,
    private val diffCalculator: StrategyDiffCalculator,
) {

    suspend fun importReport(strategyId: Int, report: ParsedStrategyReport) =
        validate(report).let {
            if (repository.exists(strategyId, it.referenceDate)) {
                throw StrategyEditionAlreadyExistsException(strategyId, it.referenceDate.toString())
            }
            repository.save(
                StrategyEditionCreation(
                    strategyId = strategyId,
                    referenceDate = it.referenceDate,
                    changesText = it.changesText,
                    targets = it.targets,
                ),
            )
        }

    suspend fun fetchEditions(strategyId: Int): List<StrategyEditionWithDiff> {
        if (!repository.strategyExists(strategyId)) {
            throw StrategyNotFoundException(strategyId)
        }

        val editions = repository.fetchByStrategyId(strategyId)
        return editions.mapIndexed { i, edition ->
            val diff = if (i == 0) null else diffCalculator.diff(editions[i - 1].targets, edition.targets)
            StrategyEditionWithDiff(edition, diff)
        }
    }

    private fun validate(parsed: ParsedStrategyReport): ParsedStrategyReport {
        if (parsed.targets.isEmpty()) {
            throw StrategyReportParseException("No targets found in report")
        }

        validateTickers(parsed)
        validateTotalWeight(parsed)

        return parsed
    }

    private fun validateTickers(parsed: ParsedStrategyReport) {
        val invalidTickers = parsed.targets.map(StrategyTarget::ticker).filterNot(TICKER_PATTERN::matches)
        if (invalidTickers.isNotEmpty()) {
            throw StrategyReportParseException("Not B3 tickers: $invalidTickers")
        }

        // A repeated ticker is a parse artifact (the same row read twice, or a performance table
        // mixed into the portfolio one). Reject it before the diff calculator, whose associateBy
        // would otherwise hide the earlier occurrence.
        val duplicatedTickers = parsed.targets.groupingBy(StrategyTarget::ticker).eachCount()
            .filterValues { it > 1 }
            .keys
        if (duplicatedTickers.isNotEmpty()) {
            throw StrategyReportParseException("Duplicated target tickers: $duplicatedTickers")
        }
    }

    private fun validateTotalWeight(parsed: ParsedStrategyReport) {
        // Weights are fractions (0.05 for 5%), same convention as AssetClassTarget. The report is
        // the broker's model portfolio, so the rows must add up to the whole portfolio exactly —
        // after the parser's scale normalization the sum has to be 1, with no rounding slack.
        val totalWeight = parsed.targets.sumOf { it.weight }
        if (totalWeight.compareTo(BigDecimal.ONE) != 0) {
            throw StrategyReportParseException("Target weights sum to $totalWeight (fraction), expected exactly 1.0")
        }
    }

    private companion object {
        // 4 alphanumeric root chars (not always pure letters — B3's own ticker is "B3SA3") plus
        // a 1-2 digit class suffix, with at least one letter overall so a bare number can't pass.
        val TICKER_PATTERN = Regex("^(?=.*[A-Z])[A-Z0-9]{4}\\d{1,2}$")
    }
}
