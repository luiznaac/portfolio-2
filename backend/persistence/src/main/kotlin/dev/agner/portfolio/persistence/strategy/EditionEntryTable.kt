package dev.agner.portfolio.persistence.strategy

import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.Rating
import dev.agner.portfolio.usecase.strategy.model.TargetCurrency
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass

object EditionEntryTable : IntIdTable("edition_entry") {
    val editionId = reference("edition_id", EditionTable.id)
    val ticker = varchar("ticker", 12)
    val weight = decimal("weight", 18, 6)
    val rating = enumerationByName<Rating>("rating", 20).nullable()
    val targetPrice = decimal("target_price", 18, 6).nullable()

    // The currency the target price is quoted in. Nullable: entries imported before the column
    // existed, and entries without a target, carry no currency.
    val targetCurrency = enumerationByName<TargetCurrency>("target_currency", 3).nullable()

    // The "não operar" flag is per-Edition only (D7): it never carries into the next edition.
    val doNotTrade = bool("do_not_trade")
}

class EditionEntryEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<EditionEntryEntity>(EditionEntryTable)

    var edition by EditionEntity referencedOn EditionEntryTable.editionId
    var ticker by EditionEntryTable.ticker
    var weight by EditionEntryTable.weight
    var rating by EditionEntryTable.rating
    var targetPrice by EditionEntryTable.targetPrice
    var targetCurrency by EditionEntryTable.targetCurrency
    var doNotTrade by EditionEntryTable.doNotTrade

    fun toModel() = EditionEntry(
        ticker = ticker,
        weight = weight,
        rating = rating,
        targetPrice = targetPrice,
        targetCurrency = targetCurrency,
        doNotTrade = doNotTrade,
    )
}
