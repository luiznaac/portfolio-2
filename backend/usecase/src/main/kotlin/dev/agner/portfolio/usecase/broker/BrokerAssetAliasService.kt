package dev.agner.portfolio.usecase.broker

import dev.agner.portfolio.usecase.broker.model.BrokerDescriptionResolution
import dev.agner.portfolio.usecase.broker.model.normalizedBrokerDescription
import dev.agner.portfolio.usecase.broker.model.withoutRightsPrice
import dev.agner.portfolio.usecase.broker.repository.IBrokerAssetAliasRepository
import dev.agner.portfolio.usecase.catalog.model.isRightsTicker
import dev.agner.portfolio.usecase.catalog.model.rightsTicker
import org.springframework.stereotype.Service

@Service
class BrokerAssetAliasService(
    private val repository: IBrokerAssetAliasRepository,
) {

    // A per-institution alias is the owner's confirmed answer for that broker and wins over the
    // global fallback. Without one, the globals answer; two globals naming different tickers are an
    // ambiguity for the owner, not a guess. The caller has already normalized the description.
    //
    // A rights row ("FII RBR PROP DM 88,27") names the underlying plus the broker's rights marker.
    // An exact alias for the whole row wins; otherwise the row is resolved through the underlying's
    // alias and the rights code is derived by B3's rule, so a new issuance price needs no new alias.
    suspend fun resolve(
        institutionId: Int?,
        normalizedDescription: String,
    ): BrokerDescriptionResolution {
        val direct = lookup(institutionId, normalizedDescription)
        val underlying = normalizedDescription.withoutRightsPrice() ?: return direct
        // An exact rights alias is the owner's answer; an exact alias that names the underlying is
        // not, so the row still resolves to the right derived from that underlying.
        if (direct is BrokerDescriptionResolution.Resolved && direct.ticker.isRightsTicker()) return direct

        val resolution = lookup(institutionId, underlying.normalizedBrokerDescription())
        return if (resolution is BrokerDescriptionResolution.Resolved) {
            BrokerDescriptionResolution.Resolved(resolution.ticker.rightsTicker())
        } else {
            direct
        }
    }

    private suspend fun lookup(
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
}

private fun Set<String>.resolution(): BrokerDescriptionResolution = when (size) {
    0 -> BrokerDescriptionResolution.Unresolved
    1 -> BrokerDescriptionResolution.Resolved(single())
    else -> BrokerDescriptionResolution.Ambiguous(this)
}
