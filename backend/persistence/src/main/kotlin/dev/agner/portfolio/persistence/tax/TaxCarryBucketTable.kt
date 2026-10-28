package dev.agner.portfolio.persistence.tax

import dev.agner.portfolio.usecase.tax.model.RealizedKind
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

// The adoption seed of the three loss-carry buckets: one row per kind, set once when the ledger is
// adopted. The running balance is not a column — it is replayed from the ledger's closed months,
// so a stored balance could only drift; the monthly-close snapshot freezes the final values.
@OptIn(ExperimentalTime::class)
object TaxCarryBucketTable : IntIdTable("tax_carry_bucket") {
    val kind = enumerationByName<RealizedKind>("kind", 20)
    val openingBalance = decimal("opening_balance", 18, 6)

    // The adoption close the balance is taken at. The carry roll skips ledger months up to and
    // including it, because those results are already part of the seeded balance.
    val asOfPeriod = varchar("as_of_period", 7)
    val createdAt = datetime("created_at")

    init { uniqueIndex(kind) }
}

@OptIn(ExperimentalTime::class)
class TaxCarryBucketEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<TaxCarryBucketEntity>(TaxCarryBucketTable)

    var kind by TaxCarryBucketTable.kind
    var openingBalance by TaxCarryBucketTable.openingBalance
    var asOfPeriod by TaxCarryBucketTable.asOfPeriod
    var createdAt by TaxCarryBucketTable.createdAt
}
