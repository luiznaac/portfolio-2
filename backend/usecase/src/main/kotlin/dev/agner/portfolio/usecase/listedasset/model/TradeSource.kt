package dev.agner.portfolio.usecase.listedasset.model

// Where a trade came from. MANUAL trades are typed in by the user; B3_IMPORT trades were
// materialized from a staged broker file; OPENING marks an adoption seed built from a B3 Posição
// snapshot. The source decides the reconciliation rules: a manual trade wins over an incoming B3 row
// (it is "confirmed by B3"), while a manual trade that dupes an already-imported B3 trade is refused.
// An OPENING seed is retired (or reduced) by the history backfill once imported trades cover it.
enum class TradeSource {
    MANUAL,
    B3_IMPORT,
    OPENING,
}
