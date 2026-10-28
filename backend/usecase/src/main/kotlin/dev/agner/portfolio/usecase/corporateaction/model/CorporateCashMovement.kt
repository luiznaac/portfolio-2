package dev.agner.portfolio.usecase.corporateaction.model

import dev.agner.portfolio.usecase.income.model.TaxNature
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// A cash movement produced by a corporate action, linked back to the event that caused it. Amounts
// are non-negative and already net for the event (gross and net are equal here: a B3 movement row
// carries one money column, so there is no withholding to derive). `taxNature` is what routes it
// away from the Income reads: a capital return is COST_REDUCTION, an amortization is
// RETURN_OF_CAPITAL, a cash bonus and an auctioned fraction are OTHER.
data class CorporateCashMovement(
    val ticker: String,
    val date: LocalDate,
    val amount: BigDecimal,
    val taxNature: TaxNature,
    val corporateActionId: Int,
)
