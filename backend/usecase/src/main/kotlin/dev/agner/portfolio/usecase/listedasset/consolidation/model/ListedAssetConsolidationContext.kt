package dev.agner.portfolio.usecase.listedasset.consolidation.model

import dev.agner.portfolio.usecase.corporateaction.model.CorporateAction
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.trade.model.Trade

data class ListedAssetConsolidationContext(
    val asset: ListedAsset,
    val trades: List<Trade>,
    val corporateActions: List<CorporateAction>,
)
