package dev.agner.portfolio.usecase.listedasset.model

import kotlinx.datetime.LocalDate

/**
 * A ticker as it was known during a period of time. A renaming (e.g. CPLE6 -> CPLE3) closes the
 * previous entry (sets [effectiveTo]) and opens a new one instead of overwriting anything, so a
 * trade or brokerage note from before the change still resolves to the right [ListedAsset].
 */
data class TickerHistoryEntry(
    val assetId: Int,
    val ticker: String,
    val effectiveFrom: LocalDate,
    val effectiveTo: LocalDate?,
)
