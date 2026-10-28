package dev.agner.portfolio.usecase.income.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// One cash movement as stored and read back: the amounts the file gave plus the Proventos lifecycle
// stage. `status` is derived on read from the payable date and any stored reconciled mark
// (IncomeLifecycle), never written by a GET; `retainedAmount` is gross minus net.
data class Income(
    val id: Int,
    val ticker: String,
    val date: LocalDate,
    val type: IncomeType,
    val grossAmount: BigDecimal,
    val retainedAmount: BigDecimal,
    val netAmount: BigDecimal,
    val taxNature: TaxNature,
    val provenance: Provenance,
    val status: IncomeStatus,
)
