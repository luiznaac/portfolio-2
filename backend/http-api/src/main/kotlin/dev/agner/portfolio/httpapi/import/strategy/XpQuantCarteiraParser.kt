package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import org.springframework.stereotype.Component

// The Top Dividendos Plus (quantitative) layout: `Companhia TICKER peso% Setor`, equal weights and
// no rating or target price. The page is recognised by having a ticker/weight table without the
// equity layout's Rating column.
@Component
class XpQuantCarteiraParser : XpTableReportParser() {

    override val name = "XpQuantCarteira"
    override val order = 20

    override fun claimsPage(page: String): Boolean =
        page.contains("Ticker") && page.contains("Peso") &&
            page.contains("Setor") && !page.contains("Rating") && !page.contains("Recomenda")

    override fun toEntry(match: MatchResult) = EditionEntry(
        ticker = match.groupValues[1].normalizedTicker(),
        weight = parsePtBrDecimal(match.groupValues[2]),
    )

    override val row = Regex(
        "$TICKER_GROUP\\s+$WEIGHT_GROUP\\s+\\S",
        RegexOption.IGNORE_CASE,
    )
}
