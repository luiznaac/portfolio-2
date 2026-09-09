package dev.agner.portfolio.usecase.listedasset.catalog.model

import dev.agner.portfolio.usecase.listedasset.model.AssetKind

// A row of B3's public universe of tickers (name + kind only) synced from brapi's
// /api/v2/tickers, independent of ListedAsset — which is "what the user actually holds". This
// exists purely to power the search-as-you-type box on the asset registration screen, so the
// user picks a ticker instead of typing its company name and kind by hand.
data class TickerCatalogEntry(
    val ticker: String,
    val name: String,
    val kind: AssetKind,
)
