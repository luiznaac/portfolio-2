package dev.agner.portfolio.usecase.broker.model

// One confirmed broker description → ticker mapping. The lookup key is the normalized description
// (see normalizedBrokerDescription); a null institution is the global fallback, so a broker without
// its own alias still resolves. A per-institution alias is the owner's answer for that broker and
// wins over the global fallback.
data class BrokerAssetAlias(
    val id: Int,
    val institutionId: Int?,
    val normalizedDescription: String,
    val ticker: String,
)
