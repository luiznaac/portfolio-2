package dev.agner.portfolio.usecase.tax.model

import kotlinx.datetime.YearMonth
import java.math.BigDecimal

// One bucket's persisted opening balance, seeded once when the ledger is adopted at a month close.
// The running balance is not stored: it is a pure roll of the ledger's months after the adoption
// boundary, so only the seed needs to survive as data. The monthly close snapshot (#174) freezes the
// final values.
data class CarryBucketOpening(
    val kind: RealizedKind,
    val openingBalance: BigDecimal,
    val asOfPeriod: YearMonth,
)
