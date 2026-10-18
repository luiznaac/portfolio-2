package dev.agner.portfolio.usecase.strategy

import dev.agner.portfolio.usecase.configuration.ITransactionTemplate
import dev.agner.portfolio.usecase.strategy.model.EditionDetail
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.EditionImport
import dev.agner.portfolio.usecase.strategy.model.EditionStatus
import dev.agner.portfolio.usecase.strategy.model.ParsedStrategyReport
import dev.agner.portfolio.usecase.strategy.model.Strategy
import dev.agner.portfolio.usecase.strategy.model.StrategyCreation
import dev.agner.portfolio.usecase.strategy.model.editionWeightProblem
import dev.agner.portfolio.usecase.strategy.model.normalizedEntries
import dev.agner.portfolio.usecase.strategy.repository.IEditionRepository
import dev.agner.portfolio.usecase.strategy.repository.IStrategyRepository
import kotlinx.datetime.YearMonth
import org.springframework.stereotype.Service

@Service
class StrategyService(
    private val strategies: IStrategyRepository,
    private val editions: IEditionRepository,
    private val diffCalculator: EditionDiffCalculator,
    private val transaction: ITransactionTemplate,
) {

    suspend fun fetchAll(): List<Strategy> = strategies.fetchAll()

    suspend fun create(creation: StrategyCreation): Strategy = strategies.save(creation)

    suspend fun fetchEditions(strategyId: Int): List<EditionDetail>? =
        strategies.fetchById(strategyId)?.let { editions.fetchByStrategy(strategyId) }

    // Stages a parsed report as a STAGED edition, diffed against the strategy's previous active one.
    // The diff is computed over canonical tickers, since the report may print a fractional-market
    // `F` suffix that the rest of the domain has already stripped. A first edition has no previous,
    // so its diff is null — the browser reads that as "primeira edição", never an empty delta.
    suspend fun importEdition(strategyId: Int, report: ParsedStrategyReport): EditionImport? {
        val strategy = strategies.fetchById(strategyId) ?: return null
        return transaction.execute {
            val previous = editions.fetchLatestActive(strategy.id, before = report.referenceMonth)
            val entries = report.entries.normalizedEntries()
            val diff = previous?.let { diffCalculator.compute(previous = it.entries, current = entries) }
            EditionImport(report.parser, editions.saveStaged(strategy.id, report.referenceMonth, entries, diff))
        }
    }

    // Confirms a staged edition with the reviewer's inline overrides. The reviewed entries must still
    // be a valid portfolio (the rule has one home: editionWeightProblem), and the diff is recomputed
    // against the previous active edition, so an overridden weight cannot leave the stored delta stale.
    suspend fun activateEdition(
        strategyId: Int,
        referenceMonth: YearMonth,
        entries: List<EditionEntry>,
    ): EditionDetail? {
        val strategy = strategies.fetchById(strategyId) ?: return null
        val staged = editions.fetch(strategy.id, referenceMonth) ?: return null
        if (staged.status != EditionStatus.STAGED) {
            throw EditionAlreadyActiveException(strategyId, referenceMonth)
        }
        val normalized = entries.normalizedEntries()
        editionWeightProblem(normalized)?.let { throw InvalidEditionException(strategyId, referenceMonth, it) }

        return transaction.execute {
            val previous = editions.fetchLatestActive(strategy.id, before = referenceMonth)
            val diff = previous?.let { diffCalculator.compute(previous = it.entries, current = normalized) }
            editions.activate(staged.id, normalized, diff)
        }
    }
}
