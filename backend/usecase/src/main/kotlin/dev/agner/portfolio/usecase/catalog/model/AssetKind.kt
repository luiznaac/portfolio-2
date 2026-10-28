package dev.agner.portfolio.usecase.catalog.model

// The kind of a listed asset, derived from B3's BDI code at catalog sync. UNKNOWN is the catch-all
// for the BDI codes this app does not classify yet (B3's smaller codes, e.g. 08/35/22) — the row
// still enters the catalog, with a logged warning, so an unclassified instrument is never dropped.
// UNIT has no BDI of its own: B3 lists units (SANB11, TAEE11) under BDI 02, same as stocks, so the
// COTAHIST specification ("UNT…") is what tells them apart.
enum class AssetKind {
    STOCK,
    UNIT,
    FII,
    BDR,
    ETF,
    UNKNOWN,
    ;

    companion object {
        fun fromBdiType(bdiType: String, specification: String? = null): AssetKind = when {
            specification?.trim()?.startsWith("UNT") == true -> UNIT
            bdiType == "02" -> STOCK
            bdiType == "12" -> FII
            bdiType == "14" -> ETF
            bdiType == "34" || bdiType == "36" -> BDR
            else -> UNKNOWN
        }
    }
}
