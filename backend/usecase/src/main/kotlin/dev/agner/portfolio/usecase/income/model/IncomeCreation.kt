package dev.agner.portfolio.usecase.income.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// One cash movement a parser produced, before it is persisted. `date` is the payable date — the
// export's "Previsão de pagamento" — and drives the lifecycle. `netAmount` is the file's
// "Valor líquido" exactly as given (already post-tax for listed-equity income, and equal to
// gross for a FII rendimento); `retainedAmount` is the withholding derived as the difference.
data class IncomeCreation(
    val ticker: String,
    val date: LocalDate,
    val type: IncomeType,
    val grossAmount: BigDecimal,
    val netAmount: BigDecimal,
    val taxNature: TaxNature = TaxNature.INCOME,
    val provenance: Provenance,
) {
    val retainedAmount: BigDecimal get() = grossAmount - netAmount
}
