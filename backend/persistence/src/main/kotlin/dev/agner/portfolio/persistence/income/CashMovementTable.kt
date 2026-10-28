package dev.agner.portfolio.persistence.income

import dev.agner.portfolio.usecase.income.model.Income
import dev.agner.portfolio.usecase.income.model.IncomeStatus
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import dev.agner.portfolio.usecase.income.model.TaxNature
import org.jetbrains.exposed.v1.core.dao.id.EntityID
import org.jetbrains.exposed.v1.core.dao.id.IntIdTable
import org.jetbrains.exposed.v1.dao.IntEntity
import org.jetbrains.exposed.v1.dao.IntEntityClass
import org.jetbrains.exposed.v1.datetime.date
import org.jetbrains.exposed.v1.datetime.datetime
import kotlin.time.ExperimentalTime

// Every cash movement a listed asset pays — income and corporate-action cash. `tax_nature`
// separates them: the Income reads select INCOME rows, so an amortization or a capital return is
// stored beside a dividend without polluting it. `status` is the stored lifecycle stage, which only
// carries information for RECONCILED — the other three are derived on read (IncomeLifecycle).
@OptIn(ExperimentalTime::class)
object CashMovementTable : IntIdTable("cash_movement") {
    val ticker = varchar("ticker", 12)
    val date = date("date")
    val grossAmount = decimal("gross_amount", 18, 6)
    val retainedAmount = decimal("retained_amount", 18, 6)
    val netAmount = decimal("net_amount", 18, 6)
    val taxNature = enumerationByName<TaxNature>("tax_nature", 20)
    val provenance = enumerationByName<Provenance>("provenance", 20)
    val status = enumerationByName<IncomeStatus>("status", 20).default(IncomeStatus.ANNOUNCED)

    // The income kind (dividend / JCP / FII rendimento), beyond the ticket's column list so the page
    // can show a "Tipo" column and the Movimentações match can route by it. Null on a
    // corporate-action cash row, whose identity is its event link, not an income type.
    val incomeType = enumerationByName<IncomeType>("income_type", 20).nullable()

    // Set on the cash a corporate action produced (a capital return, an amortization, a cash bonus,
    // an auctioned fraction); null on income.
    val corporateActionId = integer("corporate_action_id").nullable()

    val createdAt = datetime("created_at")

    // Income is per ticker, whole Position, so the natural key identifies a payment: dedup and the
    // lifecycle both hang off (ticker, payable date, type). MySQL allows repeated NULL income types,
    // so corporate-action cash does not collide with this key.
    init { uniqueIndex(ticker, date, incomeType) }
}

@OptIn(ExperimentalTime::class)
class CashMovementEntity(id: EntityID<Int>) : IntEntity(id) {
    companion object : IntEntityClass<CashMovementEntity>(CashMovementTable)

    var ticker by CashMovementTable.ticker
    var date by CashMovementTable.date
    var grossAmount by CashMovementTable.grossAmount
    var retainedAmount by CashMovementTable.retainedAmount
    var netAmount by CashMovementTable.netAmount
    var taxNature by CashMovementTable.taxNature
    var provenance by CashMovementTable.provenance
    var status by CashMovementTable.status
    var incomeType by CashMovementTable.incomeType
    var corporateActionId by CashMovementTable.corporateActionId
    var createdAt by CashMovementTable.createdAt

    fun toModel() = Income(
        id = id.value,
        ticker = ticker,
        date = date,
        type = requireNotNull(incomeType) { "Income cash movement $id has no income type" },
        grossAmount = grossAmount,
        retainedAmount = retainedAmount,
        netAmount = netAmount,
        taxNature = taxNature,
        provenance = provenance,
        status = status,
    )
}
