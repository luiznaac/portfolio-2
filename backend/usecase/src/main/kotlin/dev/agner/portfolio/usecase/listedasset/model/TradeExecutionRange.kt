package dev.agner.portfolio.usecase.listedasset.model

import kotlinx.datetime.LocalDate

// The ledger's earliest and latest execution dates. The month window derives its history bound and
// the opener's default from it, so the pair travels as one value rather than two loose dates.
data class TradeExecutionRange(
    val first: LocalDate,
    val last: LocalDate,
)
