package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.usecase.listedasset.model.Position
import org.jetbrains.exposed.v1.core.ResultRow
import org.jetbrains.exposed.v1.core.Table

// The stored projection of a listed asset's custody. The ticker is the natural key (one position
// per paper); the quantity keeps its sign so a ledger that over-sells surfaces as a negative
// position rather than being clamped away.
object PositionTable : Table("position") {
    val ticker = varchar("ticker", 12)
    val quantity = decimal("quantity", 18, 6)
    val averagePrice = decimal("average_price", 18, 6)

    override val primaryKey = PrimaryKey(ticker)
}

internal fun ResultRow.toPosition() = Position(
    ticker = this[PositionTable.ticker],
    quantity = this[PositionTable.quantity],
    averagePrice = this[PositionTable.averagePrice],
)
