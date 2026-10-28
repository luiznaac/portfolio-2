package dev.agner.portfolio.usecase.strategy.model

import kotlinx.datetime.YearMonth

// An Edition with its entries — the read shape the API returns and the preview drawer renders. The
// diff against the previous active edition is stored with the row, so a staged preview costs one
// read and the accepted history keeps its original delta.
data class EditionDetail(
    val id: Int,
    val strategyId: Int,
    val referenceMonth: YearMonth,
    val status: EditionStatus,
    val diffFromPrevious: EditionDiff?,
    val entries: List<EditionEntry>,
)
