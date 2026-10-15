package dev.agner.portfolio.usecase.catalog.model

import kotlinx.datetime.LocalDate

// One row of the ticker catalog. first_seen/last_seen are the upsert bookkeeping that lets a
// delisted ticker stay resolvable for historical positions: sync never deletes, it only moves
// last_seen forward.
data class CatalogEntry(
    val ticker: String,
    val shortName: String,
    val bdiType: String,
    val isin: String?,
    val assetKind: AssetKind,
    val assetClass: AssetClass?,
    val firstSeen: LocalDate,
    val lastSeen: LocalDate,
)
