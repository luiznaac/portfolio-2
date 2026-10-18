package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import org.springframework.stereotype.Component

// The Top Ações / Top Dividendos / Top Small Caps layout: a nine-column table whose rows print
// `Companhia TICKER peso% Rating R$ preço-alvo`. Only the summary page's table is read — the
// performance page repeats the tickers with last month's dot-decimal weights and no rating, and
// must never feed targets (issue #93's stale-table trap).
@Component
class XpEquityCarteiraParser : XpTableReportParser() {

    override val name = "XpEquityCarteira"
    override val order = 10

    override fun claimsPage(page: String): Boolean =
        page.contains("Ticker") && page.contains("Peso") &&
            page.contains("Rating") && page.contains("Preço-Alvo")

    override fun toEntry(match: MatchResult) = EditionEntry(
        ticker = match.groupValues[1].normalizedTicker(),
        weight = parsePtBrDecimal(match.groupValues[2]),
        rating = parseRating(match.groupValues[3]),
        targetPrice = parsePtBrDecimal(match.groupValues[4]),
    )

    override val row = Regex(
        "([A-Z][A-Z0-9]{3}\\d{1,2})\\s+(\\d+(?:,\\d+)?)%\\s+(Compra|Neutro|Venda)\\s+R\\$\\s*([\\d.,]+)",
        RegexOption.IGNORE_CASE,
    )
}
