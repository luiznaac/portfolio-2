package dev.agner.portfolio.usecase.broker.repository

import dev.agner.portfolio.usecase.broker.model.BrokerAssetAlias

interface IBrokerAssetAliasRepository {

    // Every alias carrying the normalized description — the per-institution rows and the global
    // (institution_id null) fallback. The service decides which one wins; the repository stays a
    // plain read so the ambiguity rule has one home.
    suspend fun fetchByDescription(normalizedDescription: String): List<BrokerAssetAlias>

    // The owner's confirmed answer: the institution's alias for the normalized description,
    // replacing that institution's previous answer for it. A null institution is the global fallback.
    suspend fun save(
        institutionId: Int?,
        normalizedDescription: String,
        ticker: String,
    ): BrokerAssetAlias
}
