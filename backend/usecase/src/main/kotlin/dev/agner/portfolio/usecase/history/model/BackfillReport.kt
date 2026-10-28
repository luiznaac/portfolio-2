package dev.agner.portfolio.usecase.history.model

// What a backfill run changed: the OPENING seeds retired (fully covered) or reduced (partially
// covered) by imported history, the GAPs created from custody drift and the GAPs retired by a true
// record. `positionsReprojected` counts the tickers whose projection was replayed.
data class BackfillReport(
    val absorbedSeeds: Int,
    val adjustedSeeds: Int,
    val gapsCreated: Int,
    val gapsReplaced: Int,
    val positionsReprojected: Int,
)
