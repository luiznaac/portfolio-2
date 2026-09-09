package dev.agner.portfolio.usecase.listedasset.model

data class ListedAssetCreation(
    val ticker: String,
    val kind: AssetKind,
    val name: String,
    val b3Identifier: String,
)
