package dev.agner.portfolio.usecase.tax.model

import dev.agner.portfolio.usecase.catalog.model.AssetKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.listedasset.model.Trade

// One ticker's whole ledger, the input the tax calculator replays. The kind comes from the ticker
// catalog and decides whether the ticker's sales feed the R$20k stock meter.
data class TickerLedger(
    val ticker: String,
    val assetKind: AssetKind,
    val trades: List<Trade>,
    val corporateActions: List<CorporateAction> = emptyList(),
)
