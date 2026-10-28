package dev.agner.portfolio.usecase.listedasset.model

// The Ativos detail read: the stored projection alongside the ledger rows it was replayed from, so
// the position and its trade history travel together.
data class PositionDetail(
    val position: Position,
    val trades: List<Trade>,
)
