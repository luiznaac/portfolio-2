package dev.agner.portfolio.usecase.portfolio.model

import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// One planning period of the Portfolio: the manually entered Capital that every Target multiplies and
// the cash Floor held back from it, plus the OPEN/CLOSED lifecycle. Targets hang off a Month as
// per-month rows, so this row anchors the whole chain and is never standing config.
//
// `aportes` is the month's net external contribution (a redemption is negative), recorded alongside
// Capital and frozen into the close snapshot: it is what the performance decomposition and the XIRR
// net out, never a movement-derived guess.
data class Month(
    val id: Int,
    val period: YearMonth,
    val capital: BigDecimal,
    val floor: BigDecimal,
    val status: MonthStatus,
    val aportes: BigDecimal = BigDecimal.ZERO,
)
