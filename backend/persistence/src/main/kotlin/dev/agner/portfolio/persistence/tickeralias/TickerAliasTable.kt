package dev.agner.portfolio.persistence.tickeralias

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.tickeralias.model.TickerAlias
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table
import org.jetbrains.exposed.v1.datetime.date

// The rename edges. The old ticker is the natural key — a code is renamed once, and a later rename
// of its successor is a separate edge (A -> B, B -> C), which is what makes the chain followable.
object TickerAliasTable : Table("ticker_alias") {
    val oldTicker = varchar("old_ticker", 12)
    val canonicalTicker = varchar("canonical_ticker", 12)
    val effectiveDate = date("effective_date")

    // `source` is taken by ColumnSet, so the property differs from the column name (as in ImportTable).
    val sourceType = enumerationByName<CorporateActionSource>("source", 20)
    val ratio = decimal("ratio", 18, 8).nullable()
    val corporateActionId = integer("corporate_action_id").nullable()

    override val primaryKey = PrimaryKey(oldTicker)
}

internal fun ResultRow.toTickerAlias() = TickerAlias(
    oldTicker = this[TickerAliasTable.oldTicker],
    canonicalTicker = this[TickerAliasTable.canonicalTicker],
    effectiveDate = this[TickerAliasTable.effectiveDate],
    source = this[TickerAliasTable.sourceType],
    ratio = this[TickerAliasTable.ratio],
    corporateActionId = this[TickerAliasTable.corporateActionId],
)
