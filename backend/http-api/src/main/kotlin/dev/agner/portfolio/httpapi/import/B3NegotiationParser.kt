package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.commons.brazilianLocalDateFormat
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// The B3 export ships the same negotiation data under two header vocabularies — the broker
// "Negociação de Ativos" layout and the Área do Investidor "Negociação" layout. A column is
// identified by any of its names, so either export claims the file.
private enum class Column(val aliases: Set<String>) {
    DATE(setOf("Data", "Data do Negócio")),
    SIDE(setOf("Tipo de Movimentação")),
    VENUE(setOf("Mercado")),
    TICKER(setOf("Ticker", "Código de Negociação")),
    QUANTITY(setOf("Quantidade")),
    UNIT_PRICE(setOf("Preço Unitário", "Preço")),
    GROSS(setOf("Valor Bruto", "Valor")),
    INSTITUTION(setOf("Instituição")),
}

// A header name mapped to the column index it occupies in the sheet. Shared with the position
// parser — both locate their columns the same way.
internal typealias ColumnIndex = Map<String, Int>

@Component
class B3NegotiationParser : IImportParser {

    // Recognition is by the header row alone, independent of filename: the layout *is* the column
    // set, and a name that merely contains "neg" is not evidence of anything. A file we cannot
    // open (non-xlsx, corrupt) is simply not ours — no throw.
    override fun shouldExecute(fileName: String, content: ByteArray): Boolean = runCatching {
        WorkbookFactory.create(content.inputStream()).use { workbook ->
            isRecognised(headerColumns(workbook.getSheetAt(0)))
        }
    }.getOrDefault(false)

    override fun parse(fileName: String, content: ByteArray): List<ImportRowPayload> =
        WorkbookFactory.create(content.inputStream()).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val headers = headerColumns(sheet)
            require(isRecognised(headers)) {
                "B3 negotiation XLSX unrecognised layout in $fileName: found headers ${headers.keys}"
            }
            parseRows(sheet, headers)
        }

    private fun isRecognised(headers: ColumnIndex) =
        Column.entries.all { column -> column.aliases.any { it in headers } }

    // Columns are located by the header cell's own position, never by an index into a compacted
    // header list: a blank or extra header before a real column would otherwise shift every later
    // column and read the wrong cell.
    private fun headerColumns(sheet: Sheet): ColumnIndex {
        val headerRow = sheet.getRow(0) ?: return emptyMap()
        return (0..<headerRow.lastCellNum.toInt())
            .mapNotNull { index ->
                val name = headerRow
                    .getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)
                    ?.stringCellValue
                    ?.trim()
                if (name.isNullOrEmpty()) null else name to index
            }
            .toMap()
    }

    private fun parseRows(sheet: Sheet, headers: ColumnIndex): List<ImportRowPayload> {
        // POI's DataFormatter is not thread-safe, so it is per-parse rather than shared on this
        // singleton component; a concurrent upload must not reuse it.
        val formatter = DataFormatter(Locale.US)
        return (1..sheet.lastRowNum).mapNotNull { rowIndex ->
            sheet.getRow(rowIndex)
                ?.takeIf { it.firstCellNum >= 0 }
                ?.let { parseRow(it, headers, formatter) }
        }
    }

    private fun parseRow(row: Row, headers: ColumnIndex, formatter: DataFormatter): ImportRowPayload {
        val date = readDate(cell(row, headers, Column.DATE), formatter)
        val sideText = readText(cell(row, headers, Column.SIDE), formatter)
        val venue = readText(cell(row, headers, Column.VENUE), formatter).orEmpty()
        val ticker = readText(cell(row, headers, Column.TICKER), formatter).orEmpty()
        val institutionName = readText(cell(row, headers, Column.INSTITUTION), formatter).orEmpty()
        val quantityCell = cell(row, headers, Column.QUANTITY)
        val unitPriceCell = cell(row, headers, Column.UNIT_PRICE)
        val grossCell = cell(row, headers, Column.GROSS)
        val side = sideFrom(sideText)
        val quantity = readNumber(quantityCell, formatter)
        val unitPrice = readNumber(unitPriceCell, formatter)
        val gross = readNumber(grossCell, formatter)

        // Every row-level problem stages the row with an `error` and a best-effort shape; none of
        // them blocks the file. A row is applicable only when `error` is null, so apply skips it.
        val error = when {
            date == null || ticker.isEmpty() -> missingDateOrTicker(date, ticker)
            side == null -> "Unknown side '$sideText'"
            quantity == null -> "Invalid quantity '${readText(quantityCell, formatter)}'"
            unitPrice == null -> "Invalid unit price '${readText(unitPriceCell, formatter)}'"
            quantity.signum() <= 0 -> "Quantity must be positive"
            else -> grossError(gross, quantity, unitPrice)
        }

        return ImportRowPayload(
            date = date,
            side = side ?: TradeSide.BUY,
            venue = venue,
            ticker = ticker.normalizedTicker(),
            quantity = quantity ?: BigDecimal.ZERO,
            unitPrice = unitPrice ?: BigDecimal.ZERO,
            institutionName = institutionName,
            error = error,
        )
    }

    private fun missingDateOrTicker(date: LocalDate?, ticker: String): String = when {
        date == null && ticker.isEmpty() -> "Missing date and ticker"
        date == null -> "Missing date"
        else -> "Missing ticker"
    }

    private fun sideFrom(sideText: String?): TradeSide? =
        when (sideText?.trim()?.uppercase()) {
            "C", "COMPRA" -> TradeSide.BUY
            "V", "VENDA" -> TradeSide.SELL
            else -> null
        }

    // Gross is derived (quantity × price), never stored: a mismatch is a row-level error, so the
    // file still stages and the row can be reviewed or dropped.
    private fun grossError(gross: BigDecimal?, quantity: BigDecimal, unitPrice: BigDecimal): String? {
        val gross = gross ?: return "Missing gross value"
        val expected = quantity.multiply(unitPrice).setScale(GROSS_SCALE, RoundingMode.HALF_UP)
        return if (gross.setScale(GROSS_SCALE, RoundingMode.HALF_UP).compareTo(expected) == 0) {
            null
        } else {
            "Gross $gross does not match quantity x price ($expected)"
        }
    }

    // A real B3 export stores the date as an Excel date cell and the numbers as numeric cells; the
    // raw `toString()` of a numeric cell is a double ("100.0") and of a date cell an Excel serial,
    // so cells are read type-aware.
    private fun readDate(cell: Cell?, formatter: DataFormatter): LocalDate? {
        if (cell == null) return null
        if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.localDateTimeCellValue.toLocalDate().toKotlinLocalDate()
        }
        val text = readText(cell, formatter) ?: return null
        // The broker layout writes an ISO date; the Área do Investidor layout writes `dd/MM/yyyy`.
        return runCatching { LocalDate.parse(text) }.getOrNull()
            ?: runCatching { LocalDate.parse(text, brazilianLocalDateFormat) }.getOrNull()
    }

    // A numeric cell is read as its number, not through `DataFormatter`: a B3 export styles the
    // money columns (e.g. `R$   26.09`), and the formatted string is not a decimal any parser can
    // read. A text cell still goes through the formatter for its own value.
    private fun readNumber(cell: Cell?, formatter: DataFormatter): BigDecimal? = when {
        cell == null -> null
        cell.cellType == CellType.NUMERIC -> BigDecimal.valueOf(cell.numericCellValue)
        else -> readText(cell, formatter)?.toBigDecimalOrNull()
    }

    private fun cell(row: Row, headers: ColumnIndex, column: Column): Cell? =
        column.aliases.firstNotNullOfOrNull { headers[it] }
            ?.let { row.getCell(it, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) }

    private companion object {
        const val GROSS_SCALE = 2
    }
}
