package dev.agner.portfolio.usecase.brokeragenote.parser

import kotlinx.datetime.LocalDate
import java.math.BigDecimal

// Port for extracting the B3 "Negociação de Ativos" XLSX export into trades. Implementation lives
// in http-api (ApachePoiBrokerageNoteParser), where the poi dependency already lives (XlsxConverter).
// No sample export was available while writing this — column names/format are a best-effort guess
// from B3's public documentation of the report, flagged here so it's easy to find and correct once
// a real file surfaces. See the plan's "Fases" §4.
interface IBrokerageNoteParser {
    fun parse(xlsxBytes: ByteArray): List<ParsedTrade>
}

enum class TradeSide { COMPRA, VENDA }

data class ParsedTrade(
    val date: LocalDate,
    val ticker: String,
    val side: TradeSide,
    val quantity: BigDecimal,
    val price: BigDecimal,
)

class BrokerageNoteParseException(message: String) : Exception(message)
