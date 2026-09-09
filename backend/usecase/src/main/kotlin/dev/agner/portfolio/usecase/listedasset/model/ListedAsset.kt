package dev.agner.portfolio.usecase.listedasset.model

data class ListedAsset(
    val id: Int,
    val ticker: String,
    val kind: AssetKind,
    val name: String,
    // Identifier used to query B3's public dividend endpoints without relying on their fuzzy
    // company search: the trading name (e.g. "PETROBRAS") for stocks, the fund identifier without
    // the "11" suffix (e.g. "KNCR") for FIIs.
    val b3Identifier: String,
)
