package dev.agner.portfolio.usecase.listedasset.gateway

import dev.agner.portfolio.usecase.listedasset.model.ListedAsset
import dev.agner.portfolio.usecase.listedasset.model.Quote

interface IQuoteGateway {

    suspend fun getQuote(asset: ListedAsset): Quote?
}
