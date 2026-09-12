package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.brokeragenote.parser.BrokerageNoteParseException
import dev.agner.portfolio.usecase.brokeragenote.parser.IBrokerageNoteParser
import dev.agner.portfolio.usecase.brokeragenote.parser.ParsedTrade
import dev.agner.portfolio.usecase.brokeragenote.parser.TradeSide
import kotlinx.datetime.LocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.math.BigDecimal

/**
 * Reads the B3 "Negociação de Ativos" XLSX export (Área do Investidor → Extrato → Negociação de
 * Ativos). No sample export was available when this was written — column names below (`Data do
 * Negócio`, `Tipo de Movimentação`, `Código de Negociação`, `Quantidade`, `Preço`, `Valor`) are a
 * best-effort guess from B3's own documentation of the report, not verified against a real file.
 * Treat as a documented best guess to correct once a real export surfaces (same pattern as
 * [PdfBoxStrategyReportParser] for the strategy-report PDF). Parsed directly with POI (not through
 * [XlsxConverter]) because that converter is wired to a fixed set of target classes via header
 * matching, and this format doesn't need that indirection.
 */
@Component
class ApachePoiBrokerageNoteParser : IBrokerageNoteParser {

    override fun parse(xlsxBytes: ByteArray): List<ParsedTrade> =
        WorkbookFactory.create(ByteArrayInputStream(xlsxBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val headerRow = sheet.getRow(0)
                ?: throw BrokerageNoteParseException("Empty spreadsheet")
            val columnIndexByHeader = headerRow.mapNotNull { it.stringValue()?.trim() }
                .withIndex()
                .associate { (i, header) -> header to i }

            val missing = REQUIRED_COLUMNS.filterNot { it in columnIndexByHeader }
            if (missing.isNotEmpty()) {
                throw BrokerageNoteParseException("Missing expected columns: $missing")
            }

            sheet.drop(1)
                .filter { row -> row.any { it.stringValue()?.isNotBlank() == true } }
                .map { row -> parseRow(row, columnIndexByHeader) }
        }

    private fun parseRow(row: Row, columnIndexByHeader: Map<String, Int>): ParsedTrade {
        fun cell(header: String): Cell? = columnIndexByHeader[header]?.let { row.getCell(it) }

        val dateText = cell(COLUMN_DATE)?.stringValue()
            ?: throw BrokerageNoteParseException("Missing $COLUMN_DATE on row ${row.rowNum + 1}")
        val sideText = cell(COLUMN_SIDE)?.stringValue()?.trim()?.uppercase()
            ?: throw BrokerageNoteParseException("Missing $COLUMN_SIDE on row ${row.rowNum + 1}")
        val ticker = cell(COLUMN_TICKER)?.stringValue()?.trim()
            ?: throw BrokerageNoteParseException("Missing $COLUMN_TICKER on row ${row.rowNum + 1}")
        val quantityText = cell(COLUMN_QUANTITY)?.stringValue()
            ?: throw BrokerageNoteParseException("Missing $COLUMN_QUANTITY on row ${row.rowNum + 1}")
        val priceText = cell(COLUMN_PRICE)?.stringValue()
            ?: throw BrokerageNoteParseException("Missing $COLUMN_PRICE on row ${row.rowNum + 1}")

        val side = when {
            sideText.startsWith("C") -> TradeSide.COMPRA
            sideText.startsWith("V") -> TradeSide.VENDA
            else -> throw BrokerageNoteParseException("Unrecognized trade side '$sideText' on row ${row.rowNum + 1}")
        }

        return ParsedTrade(
            date = parseDate(dateText, row.rowNum),
            ticker = ticker,
            side = side,
            quantity = quantityText.toBrDecimal(row.rowNum),
            price = priceText.toBrDecimal(row.rowNum),
        )
    }

    private fun parseDate(text: String, rowNum: Int): LocalDate =
        try {
            val (day, month, year) = text.trim().split("/").map { it.toInt() }
            LocalDate(year, month, day)
        } catch (_: Exception) {
            throw BrokerageNoteParseException("Unrecognized date '$text' on row ${rowNum + 1}")
        }

    private fun String.toBrDecimal(rowNum: Int): BigDecimal =
        try {
            BigDecimal(trim().replace(".", "").replace(",", "."))
        } catch (_: NumberFormatException) {
            throw BrokerageNoteParseException("Unrecognized number '$this' on row ${rowNum + 1}")
        }

    private fun Cell.stringValue(): String? = when (cellType) {
        CellType.STRING -> stringCellValue
        CellType.NUMERIC -> numericCellValue.toString()
        CellType.BLANK -> null
        else -> toString()
    }

    private companion object {
        const val COLUMN_DATE = "Data do Negócio"
        const val COLUMN_SIDE = "Tipo de Movimentação"
        const val COLUMN_TICKER = "Código de Negociação"
        const val COLUMN_QUANTITY = "Quantidade"
        const val COLUMN_PRICE = "Preço"
        val REQUIRED_COLUMNS = listOf(COLUMN_DATE, COLUMN_SIDE, COLUMN_TICKER, COLUMN_QUANTITY, COLUMN_PRICE)
    }
}
