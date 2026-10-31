package dev.agner.portfolio.usecase.catalog.model

import kotlinx.datetime.LocalDate

// The BDI stand-in for a ticker auto-entered outside a COTAHIST refresh — an import's first sighting
// of a code the catalog has never seen. It is not a derived mapping; a held ticker still carrying it
// is one no refresh file has classified.
const val UNCATALOGED_BDI_TYPE = "??"

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
    // A manual classification that outranks the BDI-derived class. Kept beside it rather than
    // replacing it, so the asset detail can say what the catalog now believes and offer a revert.
    val assetClassOverride: AssetClass? = null,
    val firstSeen: LocalDate,
    val lastSeen: LocalDate,
)

// The class every planning and attribution read uses: the owner's manual classification wins over
// the BDI-derived one, and a COTAHIST refresh never clears it. An extension rather than a member,
// so the JSON edge keeps serializing only the stored columns.
val CatalogEntry.effectiveAssetClass: AssetClass? get() = assetClassOverride ?: assetClass

// One read's ticker → effective-class map, so the override rule is applied in one place.
fun Iterable<CatalogEntry>.effectiveClassByTicker(): Map<String, AssetClass?> =
    associate { it.ticker to it.effectiveAssetClass }
