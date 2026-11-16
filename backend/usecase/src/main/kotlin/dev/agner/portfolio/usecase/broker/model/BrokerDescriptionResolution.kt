package dev.agner.portfolio.usecase.broker.model

// What the alias lookup could say about a broker description. The three cases are the whole
// contract the import review branches on: resolved writes the trade, unresolved and ambiguous stage
// a row-level error so the valid rows still apply and the description stays for the owner to
// confirm.
sealed interface BrokerDescriptionResolution {

    data class Resolved(val ticker: String) : BrokerDescriptionResolution

    data object Unresolved : BrokerDescriptionResolution

    data class Ambiguous(val tickers: Set<String>) : BrokerDescriptionResolution
}
