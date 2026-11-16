package dev.agner.portfolio.usecase.broker.repository

import dev.agner.portfolio.usecase.broker.model.BrokerAssetAlias

interface IBrokerAssetAliasRepository {

    // Every alias carrying the normalized description — the per-institution rows and the global
    // (institution_id null) fallback. The service decides which one wins; the repository stays a
    // plain read so the ambiguity rule has one home.
    suspend fun fetchByDescription(normalizedDescription: String): List<BrokerAssetAlias>
}
