package dev.agner.portfolio.persistence.listedasset

import dev.agner.portfolio.persistence.institution.InstitutionEntity
import dev.agner.portfolio.persistence.institution.InstitutionTable
import dev.agner.portfolio.usecase.listedasset.model.Trade
import kotlinx.datetime.LocalDate
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import java.math.BigDecimal
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object TradeTable : IntIdTable("trade") {
    val ticker = varchar("ticker", 12)
    val date = date("date")
    val venue = varchar("venue", 20)

    // The direction lives in the sign of this single column: positive is a buy, negative a sell.
    // The model never sees the sign — `tradeModel` translates it back to Buy/Sell.
    val quantity = decimal("quantity", 18, 6)
    val unitPrice = decimal("unit_price", 18, 6)
    val institutionId = reference("institution_id", InstitutionTable.id)
    val fee = decimal("fee", 18, 6).nullable()
    val createdAt = datetime("created_at")
}

@OptIn(ExperimentalTime::class)
class TradeEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TradeEntity>(TradeTable)

    var ticker by TradeTable.ticker
    var date by TradeTable.date
    var venue by TradeTable.venue
    var quantity by TradeTable.quantity
    var unitPrice by TradeTable.unitPrice
    var institution by InstitutionEntity referencedOn TradeTable.institutionId
    var fee by TradeTable.fee
    var createdAt by TradeTable.createdAt

    fun toModel() = tradeModel(
        id = id.value,
        ticker = ticker,
        date = date,
        venue = venue,
        quantity = quantity,
        unitPrice = unitPrice,
        institutionId = institution.id.value,
        fee = fee,
    )
}

// The one translation point between the signed storage column and the Buy/Sell algebraic type.
internal fun tradeModel(
    id: Int,
    ticker: String,
    date: LocalDate,
    venue: String,
    quantity: BigDecimal,
    unitPrice: BigDecimal,
    institutionId: Int,
    fee: BigDecimal?,
): Trade = when (quantity.signum()) {
    1 -> Trade.Buy(id, ticker, date, venue, quantity, unitPrice, institutionId, fee)
    -1 -> Trade.Sell(id, ticker, date, venue, quantity.negate(), unitPrice, institutionId, fee)
    else -> throw IllegalStateException("Trade $id has a zero quantity")
}
