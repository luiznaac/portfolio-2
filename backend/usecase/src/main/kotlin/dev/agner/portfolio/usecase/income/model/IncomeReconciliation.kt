package dev.agner.portfolio.usecase.income.model

import dev.agner.portfolio.usecase.listedasset.model.DividendType
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** One row parsed from the statement's "Movimentação" sheet — the received side of the reconciliation. */
data class ReceivedIncome(
    val date: LocalDate,
    val ticker: String,
    val type: DividendType,
    val amount: BigDecimal,
)

/**
 * Previsto (from [IncomeEvent], grouped by ticker+month+type) against recebido (from the
 * statement). A mismatch flags either a position-on-ex-date error on our side or an amount that
 * genuinely differs — the app never guesses which, just surfaces it.
 */
data class IncomeReconciliation(
    val ticker: String,
    val month: LocalDate,
    val type: DividendType,
    val previsto: BigDecimal,
    val recebido: BigDecimal,
) {
    val divergence: BigDecimal get() = recebido - previsto
    val matches: Boolean get() = divergence.signum() == 0
}
