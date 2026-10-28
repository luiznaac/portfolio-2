package dev.agner.portfolio.usecase.strategy.model

import kotlinx.datetime.YearMonth

// The confirm payload: which staged edition to activate and the reviewer's inline overrides. The
// entries are the full reviewed set, so an override can change any field or set "não operar"; the
// service recomputes the diff against the previous active edition from them.
data class EditionActivation(
    val referenceMonth: YearMonth,
    val entries: List<EditionEntry>,
)
