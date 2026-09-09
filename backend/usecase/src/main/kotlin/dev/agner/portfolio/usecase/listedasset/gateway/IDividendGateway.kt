package dev.agner.portfolio.usecase.listedasset.gateway

import dev.agner.portfolio.usecase.listedasset.model.DividendDeclaration
import dev.agner.portfolio.usecase.listedasset.model.ListedAsset

interface IDividendGateway {

    suspend fun getDividends(asset: ListedAsset): List<DividendDeclaration>
}
