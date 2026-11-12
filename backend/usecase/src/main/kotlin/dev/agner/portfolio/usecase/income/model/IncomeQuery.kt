package dev.agner.portfolio.usecase.income.model

import kotlinx.datetime.LocalDate

// The additive filters on the income ledger read. Every field is optional and absent means "no
// narrowing", so a caller that sends none gets the whole ledger exactly as before. `ticker` is the
// filter bar's case-insensitive search term, not a canonical ticker identity; `status` is the
// derived lifecycle stage, filtered after the read derives it; `from`/`to` are inclusive.
data class IncomeQuery(
    val from: LocalDate? = null,
    val to: LocalDate? = null,
    val ticker: String? = null,
    val status: IncomeStatus? = null,
)
