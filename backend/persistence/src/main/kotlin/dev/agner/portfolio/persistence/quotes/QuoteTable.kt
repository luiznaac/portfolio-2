package dev.agner.portfolio.persistence.quotes

import dev.agner.portfolio.usecase.quotes.model.Quote
import dev.agner.portfolio.usecase.quotes.model.QuoteSource
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object QuoteTable : IntIdTable("quote") {
    val ticker = varchar("ticker", 12)
    val date = date("date")
    val close = decimal("close", 18, 6)

    // `source` is taken by ColumnSet, so the property differs from the column name.
    val sourceType = enumerationByName<QuoteSource>("source", 10)
    val createdAt = datetime("created_at")

    init { uniqueIndex(ticker, date) }
}

@OptIn(ExperimentalTime::class)
class QuoteEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<QuoteEntity>(QuoteTable)

    var ticker by QuoteTable.ticker
    var date by QuoteTable.date
    var close by QuoteTable.close
    var sourceType by QuoteTable.sourceType
    var createdAt by QuoteTable.createdAt

    fun toModel() = Quote(
        ticker = ticker,
        date = date,
        close = close,
        source = sourceType,
    )
}
