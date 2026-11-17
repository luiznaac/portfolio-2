package dev.agner.portfolio.persistence.corporateaction

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSync
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

// One explicit brapi sync run: the window it covered, the counts it produced, the tickers that
// failed and the raw payloads it parsed. The raw body is LONGTEXT because a full ledger window can
// exceed a TEXT column, and it is kept so a later parser change never needs another paid month.
@OptIn(ExperimentalTime::class)
object CorporateActionSyncTable : IntIdTable("brapi_sync") {
    val startedAt = datetime("started_at")
    val asOf = date("as_of")
    val windowFrom = date("window_from")
    val windowTo = date("window_to")
    val tickerCount = integer("ticker_count")
    val fetchedCount = integer("fetched_count")
    val appliedCount = integer("applied_count")
    val parkedCount = integer("parked_count")
    val incomeCount = integer("income_count")
    val failedTickers = text("failed_tickers")
    val rawPayload = largeText("raw_payload")
}

@OptIn(ExperimentalTime::class)
class CorporateActionSyncEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CorporateActionSyncEntity>(CorporateActionSyncTable)

    var startedAt by CorporateActionSyncTable.startedAt
    var asOf by CorporateActionSyncTable.asOf
    var windowFrom by CorporateActionSyncTable.windowFrom
    var windowTo by CorporateActionSyncTable.windowTo
    var tickerCount by CorporateActionSyncTable.tickerCount
    var fetchedCount by CorporateActionSyncTable.fetchedCount
    var appliedCount by CorporateActionSyncTable.appliedCount
    var parkedCount by CorporateActionSyncTable.parkedCount
    var incomeCount by CorporateActionSyncTable.incomeCount
    var failedTickers by CorporateActionSyncTable.failedTickers
    var rawPayload by CorporateActionSyncTable.rawPayload

    fun toModel() = CorporateActionSync(
        id = id.value,
        startedAt = startedAt,
        asOf = asOf,
        windowFrom = windowFrom,
        windowTo = windowTo,
        tickerCount = tickerCount,
        fetched = fetchedCount,
        applied = appliedCount,
        parked = parkedCount,
        income = incomeCount,
        failedTickers = failedTickers.split(',').filter { it.isNotBlank() },
    )
}
