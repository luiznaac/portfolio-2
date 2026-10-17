package dev.agner.portfolio.usecase.portfolio.model

import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// Opens a month. Capital and Floor are the user's manual entry; the status starts OPEN.
data class MonthCreation(
    val period: YearMonth,
    val capital: BigDecimal,
    val floor: BigDecimal,
)
