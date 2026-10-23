package dev.agner.portfolio.usecase.performance

import java.math.BigDecimal

// The one precision contract the performance reads share: returns and benchmark percentages carry
// four decimals, and a percentage is the number (1.5 is 1.5%, not a fraction).
internal const val PERFORMANCE_SCALE = 4
internal val HUNDRED = BigDecimal("100")
