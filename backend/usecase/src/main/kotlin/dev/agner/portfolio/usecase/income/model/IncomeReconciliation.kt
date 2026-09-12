package dev.agner.portfolio.usecase.income.model

import dev.agner.portfolio.usecase.listedasset.model.DividendType
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/** One row parsed from the statement's movements sheet — the received side of the reconciliation. */
data class ReceivedIncome(
    val date: LocalDate,
    val ticker: String,
    val type: DividendType,
    val amount: BigDecimal,
)

/**
 * Expected (from [IncomeEvent], grouped by ticker + month + type) against received (from the
 * statement). A mismatch means either a wrong position on the ex-date on our side or an amount
 * that genuinely differs; the app never guesses which, it only surfaces the gap.
 */
data class IncomeReconciliation(
    val ticker: String,
    val month: LocalDate,
    val type: DividendType,
    val expected: BigDecimal,
    val received: BigDecimal,
) {
    val divergence: BigDecimal get() = received - expected
    val matches: Boolean get() = divergence.signum() == 0
}
