package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.usecase.listedasset.model.Gap
import dev.agner.portfolio.usecase.listedasset.model.GapStatus
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import java.math.BigDecimal
import kotlin.time.ExperimentalTime

// The marked reconciliation record for an unexplained custody delta (see Gap). It is deliberately a
// table of its own, not a trade: it keeps the Position replay truthful without ever entering the tax
// ledger, and a true trade retires it instead of editing it.
@OptIn(ExperimentalTime::class)
object GapTable : IntIdTable("gap") {
    val ticker = varchar("ticker", 12)
    val date = date("date")

    // The side and the nullable price are the stored form of the Gap.Buy/Gap.Sell variant; the model
    // never sees them, `gapModel` is the one translation point.
    val side = enumerationByName<TradeSide>("side", 10)
    val quantity = decimal("quantity", 18, 6)
    val unitPrice = decimal("unit_price", 18, 6).nullable()
    val status = enumerationByName<GapStatus>("status", 20)
    val tradeWatermark = integer("trade_watermark").default(0)
    val actionWatermark = integer("action_watermark").default(0)
    val createdAt = datetime("created_at")
}

@OptIn(ExperimentalTime::class)
class GapEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<GapEntity>(GapTable)

    var ticker by GapTable.ticker
    var date by GapTable.date
    var side by GapTable.side
    var quantity by GapTable.quantity
    var unitPrice by GapTable.unitPrice
    var status by GapTable.status
    var tradeWatermark by GapTable.tradeWatermark
    var actionWatermark by GapTable.actionWatermark
    var createdAt by GapTable.createdAt

    fun toModel() = gapModel(
        id = id.value,
        ticker = ticker,
        date = date,
        side = side,
        quantity = quantity,
        unitPrice = unitPrice,
        status = status,
        tradeWatermark = tradeWatermark,
        actionWatermark = actionWatermark,
    )
}

// The one translation between the stored side + nullable price and the Buy/Sell algebraic type.
internal fun gapModel(
    id: Int,
    ticker: String,
    date: kotlinx.datetime.LocalDate,
    side: TradeSide,
    quantity: BigDecimal,
    unitPrice: BigDecimal?,
    status: GapStatus,
    tradeWatermark: Int,
    actionWatermark: Int,
): Gap = when (side) {
    TradeSide.BUY -> Gap.Buy(
        id = id,
        ticker = ticker,
        date = date,
        quantity = quantity,
        unitPrice = requireNotNull(unitPrice) { "GAP $id is a missing buy but carries no unit price" },
        status = status,
        tradeWatermark = tradeWatermark,
        actionWatermark = actionWatermark,
    )

    TradeSide.SELL -> Gap.Sell(
        id = id,
        ticker = ticker,
        date = date,
        quantity = quantity,
        unitPrice = unitPrice,
        status = status,
        tradeWatermark = tradeWatermark,
        actionWatermark = actionWatermark,
    )
}
