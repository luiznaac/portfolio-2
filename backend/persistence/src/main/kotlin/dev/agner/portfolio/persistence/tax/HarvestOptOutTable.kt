package dev.agner.portfolio.persistence.tax

import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

// The tickers the owner excluded from the harvest pass. One row per ticker; deleting it re-enables
// harvesting. The set is read on every pass, so the exclusion is a stored preference, not UI state.
@OptIn(ExperimentalTime::class)
object HarvestOptOutTable : IntIdTable("harvest_opt_out") {
    val ticker = varchar("ticker", 12)
    val createdAt = datetime("created_at")

    init { uniqueIndex(ticker) }
}

@OptIn(ExperimentalTime::class)
class HarvestOptOutEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<HarvestOptOutEntity>(HarvestOptOutTable)

    var ticker by HarvestOptOutTable.ticker
    var createdAt by HarvestOptOutTable.createdAt
}
