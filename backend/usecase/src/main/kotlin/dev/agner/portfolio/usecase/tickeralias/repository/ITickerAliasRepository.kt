package dev.agner.portfolio.usecase.tickeralias.repository

import dev.agner.portfolio.usecase.tickeralias.model.TickerAlias

// The ticker-identity port, implemented against Exposed in persistence. It stores the raw edges; the
// resolver is what follows a chain and returns the alias set a read must query.
interface ITickerAliasRepository {

    suspend fun fetchAll(): List<TickerAlias>

    // Upsert keyed on the old ticker: one rename edge per old code, so re-importing the same
    // TICKER_CHANGE overwrites its edge instead of duplicating the chain.
    suspend fun save(alias: TickerAlias): TickerAlias
}
