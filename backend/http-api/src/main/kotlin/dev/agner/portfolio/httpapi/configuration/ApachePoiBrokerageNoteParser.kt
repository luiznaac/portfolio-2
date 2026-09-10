package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.brokeragenote.parser.BrokerageNoteParseException
import dev.agner.portfolio.usecase.brokeragenote.parser.IBrokerageNoteParser
import dev.agner.portfolio.usecase.brokeragenote.parser.ParsedTrade
import dev.agner.portfolio.usecase.trade.model.TradeSide
import org.springframework.stereotype.Component

/**
 * Reads the B3 "Negociação de Ativos" export (Área do Investidor → Extrato → Negociação de
 * Ativos). Verified against a real September 2026 export: one sheet named "Negociação", header row
 * `Data do Negócio | Tipo de Movimentação | Mercado | Prazo/Vencimento | Instituição | Código de
 * Negociação | Quantidade | Preço | Valor`, one execution per row.
 *
 * Uses POI directly through [XlsxSheetReader] rather than [XlsxConverter], which matches headers
 * against a fixed set of target classes this format doesn't need.
 */
@Component
class ApachePoiBrokerageNoteParser : IBrokerageNoteParser {

    override fun parse(xlsxBytes: ByteArray): List<ParsedTrade> {
        val reader = XlsxSheetReader.open(xlsxBytes, REQUIRED_COLUMNS, ::fail)

        return with(reader) {
            dataRows().map { row ->
                ParsedTrade(
                    date = row.requiredDate(COLUMN_DATE),
                    ticker = row.requiredText(COLUMN_TICKER).canonicalTicker(),
                    side = row.requiredText(COLUMN_SIDE).toSide(row.rowNum),
                    quantity = row.requiredDecimal(COLUMN_QUANTITY),
                    price = row.requiredDecimal(COLUMN_PRICE),
                )
            }
        }
    }

    /**
     * A fractional-market execution ("Mercado Fracionário") lists the ticker with a trailing `F`
     * — `ALUP11F`, `B3SA3F` — but it is the same paper as `ALUP11` / `B3SA3`, just a sub-100-share
     * lot. B3's class code is always numeric, so a `F` after `<4 alphanumerics><1–2 digits>` is
     * unambiguously the fractional suffix and is stripped.
     */
    private fun String.canonicalTicker(): String = FRACTIONAL_TICKER.matchEntire(this)?.groupValues?.get(1) ?: this

    private fun String.toSide(rowNum: Int): TradeSide = when (uppercase().firstOrNull()) {
        'C' -> TradeSide.BUY
        'V' -> TradeSide.SELL
        else -> fail("Unrecognized trade side '$this' on row ${rowNum + 1}")
    }

    private companion object {
        const val COLUMN_DATE = "Data do Negócio"
        const val COLUMN_SIDE = "Tipo de Movimentação"
        const val COLUMN_TICKER = "Código de Negociação"
        const val COLUMN_QUANTITY = "Quantidade"
        const val COLUMN_PRICE = "Preço"
        val REQUIRED_COLUMNS = listOf(COLUMN_DATE, COLUMN_SIDE, COLUMN_TICKER, COLUMN_QUANTITY, COLUMN_PRICE)

        val FRACTIONAL_TICKER = Regex("""([A-Z0-9]{4}\d{1,2})F""")

        fun fail(message: String): Nothing = throw BrokerageNoteParseException(message)
    }
}
