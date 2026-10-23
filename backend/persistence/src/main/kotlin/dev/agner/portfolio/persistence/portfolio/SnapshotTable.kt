package dev.agner.portfolio.persistence.portfolio

import dev.agner.portfolio.usecase.configuration.JsonMapper
import dev.agner.portfolio.usecase.monthlyclose.model.CloseState
import dev.agner.portfolio.usecase.monthlyclose.model.Snapshot
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

// The immutable close snapshot: one row per month, the whole close state serialized as JSON. The
// month unique key is the immutability guard at the database's level; the repository never updates
// the row, so a correction goes through the ledger into the next month's snapshot instead.
@OptIn(ExperimentalTime::class)
object SnapshotTable : IntIdTable("snapshot") {
    val monthId = reference("month_id", MonthTable.id)

    // LONGTEXT, not TEXT: the document carries the whole book and grows with the portfolio, so the
    // 64 KiB TEXT ceiling would eventually reject a legitimate close.
    val state = largeText("state")

    val createdAt = datetime("created_at")

    init { uniqueIndex(monthId) }
}

@OptIn(ExperimentalTime::class)
class SnapshotEntity(id: EntityID<Int>) : IntEntity(id) {

    var month by MonthEntity referencedOn SnapshotTable.monthId
    var state by SnapshotTable.state
    var createdAt by SnapshotTable.createdAt

    fun toModel() = Snapshot(
        id = id.value,
        monthId = month.id.value,
        state = JsonMapper.mapper.readValue(state, CloseState::class.java),
        createdAt = createdAt,
    )

    companion object : IntEntityClass<SnapshotEntity>(SnapshotTable)
}
