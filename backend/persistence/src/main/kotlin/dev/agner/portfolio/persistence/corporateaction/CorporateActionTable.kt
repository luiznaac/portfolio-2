package dev.agner.portfolio.persistence.corporateaction

import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionStatus
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

// The corporate-action log. The row is a holder-neutral template (ratio, declared per-share value,
// dates) plus the engine's decision; the holder's quantities and amounts are never stored here —
// the position replay and the cash ledger derive them. `ordinal` keeps two same-day events of one
// kind distinct and gives the replay a deterministic order; the natural key is
// (ticker, kind, ex_date, ordinal).
@OptIn(ExperimentalTime::class)
object CorporateActionTable : IntIdTable("corporate_action") {
    val ticker = varchar("ticker", 12)
    val kind = enumerationByName<CorporateActionKind>("kind", 30)
    val exDate = date("ex_date")
    val approvalDate = date("approval_date").nullable()
    val ratio = decimal("ratio", 18, 8).nullable()
    val declaredValuePerShare = decimal("declared_value_per_share", 18, 8).nullable()
    val targetTicker = varchar("target_ticker", 12).nullable()

    // `source` is taken by ColumnSet, so the property differs from the column name (as in TradeTable).
    val sourceType = enumerationByName<CorporateActionSource>("source", 20)
    val processNumber = varchar("process_number", 40).nullable()
    val rawType = varchar("raw_type", 80).nullable()
    val ordinal = integer("ordinal").default(1)
    val fileHash = varchar("file_hash", 64).nullable()
    val status = enumerationByName<CorporateActionStatus>("status", 20)
    val createdAt = datetime("created_at")

    init { uniqueIndex(ticker, kind, exDate, ordinal) }
}

@OptIn(ExperimentalTime::class)
class CorporateActionEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CorporateActionEntity>(CorporateActionTable)

    var ticker by CorporateActionTable.ticker
    var kind by CorporateActionTable.kind
    var exDate by CorporateActionTable.exDate
    var approvalDate by CorporateActionTable.approvalDate
    var ratio by CorporateActionTable.ratio
    var declaredValuePerShare by CorporateActionTable.declaredValuePerShare
    var targetTicker by CorporateActionTable.targetTicker
    var sourceType by CorporateActionTable.sourceType
    var processNumber by CorporateActionTable.processNumber
    var rawType by CorporateActionTable.rawType
    var ordinal by CorporateActionTable.ordinal
    var fileHash by CorporateActionTable.fileHash
    var status by CorporateActionTable.status
    var createdAt by CorporateActionTable.createdAt

    fun toModel() = CorporateAction(
        id = id.value,
        ticker = ticker,
        kind = kind,
        exDate = exDate,
        approvalDate = approvalDate,
        ratio = ratio,
        declaredValuePerShare = declaredValuePerShare,
        targetTicker = targetTicker,
        source = sourceType,
        processNumber = processNumber,
        rawType = rawType,
        ordinal = ordinal,
        fileHash = fileHash,
        status = status,
    )
}
