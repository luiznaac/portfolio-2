package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.strategy.model.EditionDetail
import dev.agner.portfolio.usecase.strategy.model.EditionDiff
import dev.agner.portfolio.usecase.strategy.model.EditionStatus
import kotlinx.datetime.YearMonth
import org.jetbrains.exposed.v1.core.SortOrder
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.core.eq
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object EditionTable : IntIdTable("edition") {
    val strategyId = reference("strategy_id", StrategyTable.id)

    // Stored as the ISO "YYYY-MM" string the edge speaks, like the month's period. One edition per
    // strategy per month is the natural key; a corrected report replaces the staged row.
    val referenceMonth = varchar("reference_month", 7)
    val status = enumerationByName<EditionStatus>("status", 20)

    // The diff computed against the previous active edition, serialized as JSON. Stored rather than
    // recomputed so a staged preview is a read and an accepted edition keeps its original delta.
    val diffFromPreviousStored = text("diff_from_previous_stored").nullable()
    val createdAt = datetime("created_at")

    init { uniqueIndex(strategyId, referenceMonth) }
}

@OptIn(ExperimentalTime::class)
class EditionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EditionEntity>(EditionTable)

    var strategy by StrategyEntity referencedOn EditionTable.strategyId
    var referenceMonth by EditionTable.referenceMonth
    var status by EditionTable.status
    var diffFromPreviousStored by EditionTable.diffFromPreviousStored
    var createdAt by EditionTable.createdAt

    fun toDetail() = EditionDetail(
        id = id.value,
        strategyId = strategy.id.value,
        referenceMonth = YearMonth.parse(referenceMonth),
        status = status,
        diffFromPrevious = diffFromPreviousStored?.let {
            JsonMapper.mapper.readValue(it, EditionDiff::class.java)
        },
        entries = EditionEntryEntity.find { EditionEntryTable.editionId eq id }
            .orderBy(EditionEntryTable.id to SortOrder.ASC)
            .map { it.toModel() },
    )
}
