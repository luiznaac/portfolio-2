package dev.agner.portfolio.usecase.strategy.repository

import dev.agner.portfolio.usecase.strategy.model.EditionDetail
import dev.agner.portfolio.usecase.strategy.model.EditionDiff
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import kotlinx.datetime.YearMonth

interface IEditionRepository {

    suspend fun fetchByStrategy(strategyId: Int): List<EditionDetail>

    suspend fun fetch(strategyId: Int, referenceMonth: YearMonth): EditionDetail?

    // The strategy's current edition, or — with `before` — the newest active edition published
    // before that month. The diff of a new edition is computed against the latter.
    suspend fun fetchLatestActive(strategyId: Int, before: YearMonth? = null): EditionDetail?

    // A corrected report replaces the staged edition for the same month. The edition and its
    // entries are written in one transaction, so a partial table can never land.
    suspend fun saveStaged(
        strategyId: Int,
        referenceMonth: YearMonth,
        entries: List<EditionEntry>,
        diff: EditionDiff?,
    ): EditionDetail

    suspend fun activate(editionId: Int, entries: List<EditionEntry>, diff: EditionDiff?): EditionDetail
}
