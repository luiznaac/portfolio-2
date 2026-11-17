package dev.agner.portfolio.usecase.broker

import dev.agner.portfolio.usecase.broker.model.BrokerAssetAlias
import dev.agner.portfolio.usecase.broker.model.BrokerDescriptionResolution
import dev.agner.portfolio.usecase.broker.repository.IBrokerAssetAliasRepository
import org.springframework.stereotype.Service

@Service
class BrokerAssetAliasService(
    private val repository: IBrokerAssetAliasRepository,
) {

    // A per-institution alias is the owner's confirmed answer for that broker and wins over the
    // global fallback. Without one, the globals answer; two globals naming different tickers are an
    // ambiguity for the owner, not a guess. The caller has already normalized the description.
    suspend fun resolve(
        institutionId: Int?,
        normalizedDescription: String,
    ): BrokerDescriptionResolution {
        val aliases = repository.fetchByDescription(normalizedDescription)
        val own = aliases
            .filter { alias -> institutionId != null && alias.institutionId == institutionId }
            .map { it.ticker }
            .toSet()
        if (own.isNotEmpty()) return own.resolution()

        val global = aliases
            .filter { it.institutionId == null }
            .map { it.ticker }
            .toSet()
        return global.resolution()
    }

    // The owner's answer to a description the import review could not resolve. It lands as the
    // institution's own alias — that broker's answer, which wins over the global fallback on the
    // next note — and never as a second global that could make the description ambiguous. The
    // caller has already normalized the description.
    suspend fun confirm(
        institutionId: Int?,
        normalizedDescription: String,
        ticker: String,
    ): BrokerAssetAlias = repository.save(institutionId, normalizedDescription, ticker)
}

private fun Set<String>.resolution(): BrokerDescriptionResolution = when (size) {
    0 -> BrokerDescriptionResolution.Unresolved
    1 -> BrokerDescriptionResolution.Resolved(single())
    else -> BrokerDescriptionResolution.Ambiguous(this)
}
