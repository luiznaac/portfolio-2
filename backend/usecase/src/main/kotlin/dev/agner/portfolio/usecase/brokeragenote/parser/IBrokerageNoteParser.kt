package dev.agner.portfolio.usecase.brokeragenote.parser

import dev.agner.portfolio.usecase.trade.model.TradeSide
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

/**
 * Port for extracting the B3 "Negociação de Ativos" statement export into trades. Implemented in
 * `http-api`, where the POI dependency already lives.
 *
 * No sample export was available while this was written — the column names the implementation
 * looks for are a best-effort reading of B3's own documentation, not verified against a real file.
 */
interface IBrokerageNoteParser {
    fun parse(xlsxBytes: ByteArray): List<ParsedTrade>
}

data class ParsedTrade(
    val date: LocalDate,
    val ticker: String,
    val side: TradeSide,
    val quantity: BigDecimal,
    val price: BigDecimal,
)

class BrokerageNoteParseException(message: String) : Exception(message)
