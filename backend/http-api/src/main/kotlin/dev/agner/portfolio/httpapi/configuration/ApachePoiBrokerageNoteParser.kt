package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.brokeragenote.parser.BrokerageNoteParseException
import dev.agner.portfolio.usecase.brokeragenote.parser.IBrokerageNoteParser
import dev.agner.portfolio.usecase.brokeragenote.parser.ParsedTrade
import dev.agner.portfolio.usecase.trade.model.TradeSide
import org.springframework.stereotype.Component

/**
 * Reads the B3 "Negociação de Ativos" export (Área do Investidor → Extrato → Negociação de
 * Ativos). No sample export was available when this was written — the column names below are a
 * best-effort reading of B3's own documentation, not verified against a real file. Correct them
 * against the first real export.
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
                    ticker = row.requiredText(COLUMN_TICKER),
                    side = row.requiredText(COLUMN_SIDE).toSide(row.rowNum),
                    quantity = row.requiredDecimal(COLUMN_QUANTITY),
                    price = row.requiredDecimal(COLUMN_PRICE),
                )
            }
        }
    }

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

        fun fail(message: String): Nothing = throw BrokerageNoteParseException(message)
    }
}
