package dev.agner.portfolio.usecase.listedasset.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class DividendType {
    DIVIDEND,

    /** Juros sobre Capital Próprio — a Brazilian instrument with no English equivalent. */
    JCP,

    /** A fund's monthly distribution ("rendimento"), the FII equivalent of a dividend. */
    FUND_INCOME,
}

/**
 * A distribution as declared by B3, gross per share. This is NOT what lands in the account: JCP
 * has IRRF withheld at source, while dividends and fund income are tax-free for individuals —
 * which is why it is reconciled against the broker statement rather than trusted outright.
 */
data class DividendDeclaration(
    val type: DividendType,
    val valuePerShare: BigDecimal,
    val exDate: LocalDate,
    val paymentDate: LocalDate?,
)
