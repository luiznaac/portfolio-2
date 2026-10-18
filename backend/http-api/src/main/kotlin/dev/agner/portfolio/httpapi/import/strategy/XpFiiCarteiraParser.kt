package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import org.springframework.stereotype.Component

// The Fundamentalista FII layout: a wide table whose rows print
// `peso% Segmento TICKER Recomendação Nome VM Cota VP VM/VP No mês Em 12m DY 12m Risco`.
// The table appears twice (summary and detail pages); the first occurrence is read. The main table
// carries no target price — the per-fund cards do, but their labels are font-encoded
// inconsistently across samples, so the reviewer sets that override in the preview drawer.
@Component
class XpFiiCarteiraParser : XpTableReportParser() {

    override val name = "XpFiiCarteira"
    override val order = 30

    override fun claimsPage(page: String): Boolean =
        page.contains("Recomenda") && page.contains("DY 12m")

    override fun toEntry(match: MatchResult) = EditionEntry(
        ticker = match.groupValues[3].normalizedTicker(),
        weight = parsePtBrDecimal(match.groupValues[1]),
        rating = parseRating(match.groupValues[4]),
    )

    override val row = Regex(
        "(\\d+(?:,\\d+)?)%\\s+(\\S.*?)\\s+([A-Z][A-Z0-9]{3}\\d{1,2})\\s+(COMPRA|NEUTRO|VENDA)\\s+" +
            "(.+?)\\s+([\\d.]+)\\s+(\\d+)\\s+(\\d+)\\s+(\\d+)%\\s+(-?[\\d,]+%)\\s+" +
            "(-?[\\d,]+%)\\s+([\\d,]+%)\\s+(\\d+)\\s*$",
        RegexOption.IGNORE_CASE,
    )
}
