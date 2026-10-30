package dev.agner.portfolio.usecase.catalog.gateway

// A COTAHIST download or parse that could not produce rows. The caller must see the failure instead
// of a silent empty result: a refresh that stores nothing is an outage, not a quote of zero.
class CotahistFetchException(message: String, cause: Throwable? = null) : RuntimeException(message, cause)
