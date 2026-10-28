package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.usecase.commons.now
import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.strategy.EditionAlreadyActiveException
import dev.agner.portfolio.usecase.strategy.model.EditionDetail
import dev.agner.portfolio.usecase.strategy.model.EditionDiff
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.EditionStatus
import dev.agner.portfolio.usecase.strategy.repository.IEditionRepository
import kotlinx.datetime.LocalDateTime
import kotlinx.datetime.YearMonth
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.and
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.core.less
import org.jetbrains.exposed.v1.jdbc.transactions.transaction
import org.springframework.stereotype.Component
import java.time.Clock

@Component
class EditionRepository(
    private val clock: Clock,
) : IEditionRepository {

    override suspend fun fetchByStrategy(strategyId: Int): List<EditionDetail> = transaction {
        EditionEntity.find { EditionTable.strategyId eq strategyId }
            .orderBy(EditionTable.referenceMonth to SortOrder.DESC)
            .map { it.toDetail() }
    }

    override suspend fun fetch(strategyId: Int, referenceMonth: YearMonth): EditionDetail? = transaction {
        find(strategyId, referenceMonth)?.toDetail()
    }

    override suspend fun fetchLatestActive(strategyId: Int, before: YearMonth?): EditionDetail? = transaction {
        EditionEntity.find {
            val active = (EditionTable.strategyId eq strategyId) and
                (EditionTable.status eq EditionStatus.ACTIVE)
            if (before == null) active else active and (EditionTable.referenceMonth less before.toString())
        }
            .orderBy(EditionTable.referenceMonth to SortOrder.DESC)
            .firstOrNull()
            ?.toDetail()
    }

    override suspend fun saveStaged(
        strategyId: Int,
        referenceMonth: YearMonth,
        entries: List<EditionEntry>,
        diff: EditionDiff?,
    ): EditionDetail = transaction {
        val existing = find(strategyId, referenceMonth)

        val edition = when {
            existing == null -> EditionEntity.new {
                strategy = StrategyEntity[strategyId]
                this.referenceMonth = referenceMonth.toString()
                status = EditionStatus.STAGED
                createdAt = LocalDateTime.now(clock)
            }

            existing.status == EditionStatus.ACTIVE -> throw EditionAlreadyActiveException(strategyId, referenceMonth)

            else -> existing
        }

        replaceEntries(edition, entries)
        edition.diffFromPreviousStored = diff?.stored()
        edition.status = EditionStatus.STAGED
        edition.toDetail()
    }

    override suspend fun activate(
        editionId: Int,
        entries: List<EditionEntry>,
        diff: EditionDiff?,
    ): EditionDetail = transaction {
        val edition = EditionEntity[editionId]
        replaceEntries(edition, entries)
        edition.diffFromPreviousStored = diff?.stored()
        edition.status = EditionStatus.ACTIVE
        edition.toDetail()
    }

    // The strategy × month identity both reads share; the caller runs inside a transaction.
    private fun find(strategyId: Int, referenceMonth: YearMonth): EditionEntity? =
        EditionEntity.find {
            (EditionTable.strategyId eq strategyId) and
                (EditionTable.referenceMonth eq referenceMonth.toString())
        }.firstOrNull()

    // An edition's entries are replaced wholesale: a corrected report is the new truth, not a merge.
    private fun replaceEntries(edition: EditionEntity, entries: List<EditionEntry>) {
        EditionEntryEntity.find { EditionEntryTable.editionId eq edition.id }.forEach { it.delete() }
        for (entry in entries) {
            EditionEntryEntity.new {
                this.edition = edition
                ticker = entry.ticker
                weight = entry.weight
                rating = entry.rating
                targetPrice = entry.targetPrice
                doNotTrade = entry.doNotTrade
            }
        }
    }

    private fun EditionDiff.stored(): String = JsonMapper.mapper.writeValueAsString(this)
}
