package dev.agner.portfolio.usecase.listedasset.model

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

enum class DividendType {
    DIVIDENDO,
    JCP,
    RENDIMENTO,
}

/**
 * A provento as declared by B3, gross per share. This is NOT what lands in the account: JCP has
 * 15% withheld at source, DIVIDENDO and RENDIMENTO are tax-free for individuals. See the plan's
 * section on proventos for why this is reconciled against the broker statement rather than trusted
 * outright.
 */
data class DividendDeclaration(
    val type: DividendType,
    val valuePerShare: BigDecimal,
    val exDate: LocalDate,
    val paymentDate: LocalDate?,
)
