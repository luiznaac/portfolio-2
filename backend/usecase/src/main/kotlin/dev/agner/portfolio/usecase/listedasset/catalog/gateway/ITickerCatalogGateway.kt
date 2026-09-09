package dev.agner.portfolio.usecase.listedasset.catalog.gateway

import dev.agner.portfolio.usecase.listedasset.catalog.model.TickerCatalogEntry

interface ITickerCatalogGateway {
    suspend fun fetchAll(): List<TickerCatalogEntry>
}
