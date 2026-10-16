package dev.agner.portfolio.persistence.import

import dev.agner.portfolio.usecase.import.model.ImportRow
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

@OptIn(ExperimentalTime::class)
object ImportRowTable : IntIdTable("import_row") {
    val importId = reference("import_id", ImportTable.id)

    // Nullable: a row whose date is missing or unparseable stages with a null date and an error.
    val date = date("date").nullable()
    val side = enumerationByName<TradeSide>("side", 4)
    val venue = varchar("venue", 20)
    val ticker = varchar("ticker", 12)
    val quantity = decimal("quantity", 18, 6)
    val unitPrice = decimal("unit_price", 18, 6)
    val institutionName = varchar("institution_name", 100)

    // Non-null marks a row-level problem; apply skips flagged rows.
    val error = varchar("error", 255).nullable()
    val createdAt = datetime("created_at")
}

@OptIn(ExperimentalTime::class)
class ImportRowEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<ImportRowEntity>(ImportRowTable)

    // `import` is a Kotlin keyword, so the owning relation is named `owner`.
    var owner by ImportEntity referencedOn ImportRowTable.importId
    var date by ImportRowTable.date
    var side by ImportRowTable.side
    var venue by ImportRowTable.venue
    var ticker by ImportRowTable.ticker
    var quantity by ImportRowTable.quantity
    var unitPrice by ImportRowTable.unitPrice
    var institutionName by ImportRowTable.institutionName
    var error by ImportRowTable.error
    var createdAt by ImportRowTable.createdAt

    fun toModel() = ImportRow(
        id = id.value,
        importId = owner.id.value,
        date = date,
        side = side,
        venue = venue,
        ticker = ticker,
        quantity = quantity,
        unitPrice = unitPrice,
        institutionName = institutionName,
        error = error,
    )
}
