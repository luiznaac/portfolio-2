package dev.agner.portfolio.usecase.broker.model

// The B3 class/marker suffixes a broker note appends to an asset description. They say nothing
// about which paper it is, so the alias lookup key drops them: "FII BCFF11 CI" and "FII BCFF11"
// are the same asset, and a later note with a different marker must not ask for a new alias.
private val BROKER_MARKERS = setOf("CI", "ER", "NM", "N1", "N2", "ED", "EDJ", "EJ")

private val HASH_MARKER = Regex("#\\d*")
private val WHITESPACE_RUN = Regex("\\s+")

// A rights row prints the underlying description followed by "DM <price>" (e.g. "FII RBR PROP DM
// 88,27"). The marker is the broker's rights flag, and the price changes with the issuance, so it is
// never part of the underlying's alias key.
private val RIGHTS_PRICE_MARKER = Regex("\\s+DM\\s+[\\d.,]+$")

// The canonical form of a broker asset description, used as the Broker Asset Alias lookup key:
// uppercase, the marker tokens and the `#`/`#2` suffixes removed, whitespace collapsed. Tokens are
// compared whole, so a company name containing "CI" as a substring is left alone.
fun String.normalizedBrokerDescription(): String =
    replace(HASH_MARKER, " ")
        .uppercase()
        .split(WHITESPACE_RUN)
        .filter { it.isNotBlank() && it !in BROKER_MARKERS }
        .joinToString(" ")

// Whether a broker description is a rights row. The row resolves to the rights ticker, never the
// underlying: a right is its own paper with its own price and tax book.
fun String.isRightsDescription(): Boolean = RIGHTS_PRICE_MARKER.containsMatchIn(this)

// The underlying description a rights row refers to, with the "DM <price>" marker removed; null
// when the description carries no marker. It is the key an alias lookup can resolve before the
// rights code is derived.
fun String.withoutRightsPrice(): String? =
    RIGHTS_PRICE_MARKER.replace(this, "").takeIf { it != this }
