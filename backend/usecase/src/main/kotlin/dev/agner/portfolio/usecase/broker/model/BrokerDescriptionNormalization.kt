package dev.agner.portfolio.usecase.broker.model

// The B3 class/marker suffixes a broker note appends to an asset description. They say nothing
// about which paper it is, so the alias lookup key drops them: "FII BCFF11 CI" and "FII BCFF11"
// are the same asset, and a later note with a different marker must not ask for a new alias.
private val BROKER_MARKERS = setOf("CI", "ER", "NM", "N1", "N2", "ED", "EDJ", "EJ")

private val HASH_MARKER = Regex("#\\d*")
private val WHITESPACE_RUN = Regex("\\s+")

// The canonical form of a broker asset description, used as the Broker Asset Alias lookup key:
// uppercase, the marker tokens and the `#`/`#2` suffixes removed, whitespace collapsed. Tokens are
// compared whole, so a company name containing "CI" as a substring is left alone.
fun String.normalizedBrokerDescription(): String =
    replace(HASH_MARKER, " ")
        .uppercase()
        .split(WHITESPACE_RUN)
        .filter { it.isNotBlank() && it !in BROKER_MARKERS }
        .joinToString(" ")
