package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.strategy.model.StrategyEditionCreation
import dev.agner.portfolio.usecase.strategy.model.StrategyEditionWithDiff
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.IStrategyReportParser
import dev.agner.portfolio.usecase.strategy.parser.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import dev.agner.portfolio.usecase.strategy.repository.IStrategyEditionRepository
import org.springframework.stereotype.Service
import java.math.BigDecimal

/**
 * Imports a broker model-portfolio PDF into a new, immutable
 * [dev.agner.portfolio.usecase.strategy.model.StrategyEdition]
 * and exposes the diff against the previous one. Hard validation before saving — a bad parse
 * (page-3 "Desempenho" table instead of page-1 targets, a truncated table) fails loudly instead
 * of silently corrupting a strategy's weights. See the plan's "Fases" §2.
 */
@Service
class StrategyEditionService(
    private val repository: IStrategyEditionRepository,
    private val parser: IStrategyReportParser,
    private val diffCalculator: StrategyDiffCalculator,
) {

    suspend fun importReport(strategyId: Int, pdfBytes: ByteArray) = validate(parser.parse(pdfBytes)).let {
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

        val invalidTickers = parsed.targets.map(StrategyTarget::ticker).filterNot(TICKER_PATTERN::matches)
        if (invalidTickers.isNotEmpty()) {
            throw StrategyReportParseException("Not B3 tickers: $invalidTickers")
        }

        val totalWeight = parsed.targets.sumOf { it.weight }
        if (totalWeight !in WEIGHT_TOLERANCE) {
            throw StrategyReportParseException("Target weights sum to $totalWeight (fraction), expected ~1.0")
        }

        return parsed
    }

    private companion object {
        // 4 alphanumeric root chars (not always pure letters — B3's own ticker is "B3SA3") plus
        // a 1-2 digit class suffix, with at least one letter overall so a bare number can't pass.
        val TICKER_PATTERN = Regex("^(?=.*[A-Z])[A-Z0-9]{4}\\d{1,2}$")

        // weight is a fraction (0.05 for 5%), same convention as AssetClassTarget — allow a
        // little slack since PDF tables round individual rows.
        val WEIGHT_TOLERANCE = BigDecimal("0.99")..BigDecimal("1.01")
    }
}
