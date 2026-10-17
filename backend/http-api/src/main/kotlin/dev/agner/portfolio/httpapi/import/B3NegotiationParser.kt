package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
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

    private fun isRecognised(headers: ColumnIndex) = EXPECTED_HEADERS.all { it in headers }

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
        val date = readDate(cell(row, headers, "Data"), formatter)
        val sideText = readText(cell(row, headers, "Tipo de Movimentação"), formatter)
        val venue = readText(cell(row, headers, "Mercado"), formatter).orEmpty()
        val ticker = readText(cell(row, headers, "Ticker"), formatter).orEmpty()
        val institutionName = readText(cell(row, headers, "Instituição"), formatter).orEmpty()
        val quantityText = readText(cell(row, headers, "Quantidade"), formatter)
        val unitPriceText = readText(cell(row, headers, "Preço Unitário"), formatter)
        val grossText = readText(cell(row, headers, "Valor Bruto"), formatter)
        val side = sideFrom(sideText)
        val quantity = quantityText?.toBigDecimalOrNull()
        val unitPrice = unitPriceText?.toBigDecimalOrNull()

        // Every row-level problem stages the row with an `error` and a best-effort shape; none of
        // them blocks the file. A row is applicable only when `error` is null, so apply skips it.
        val error = when {
            date == null || ticker.isEmpty() -> missingDateOrTicker(date, ticker)
            side == null -> "Unknown side '$sideText'"
            quantity == null -> "Invalid quantity '$quantityText'"
            unitPrice == null -> "Invalid unit price '$unitPriceText'"
            quantity.signum() <= 0 -> "Quantity must be positive"
            else -> grossError(grossText, quantity, unitPrice)
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
    private fun grossError(grossText: String?, quantity: BigDecimal, unitPrice: BigDecimal): String? {
        val gross = grossText?.toBigDecimalOrNull() ?: return "Missing gross value"
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
        return runCatching { LocalDate.parse(text) }.getOrNull()
    }

    private companion object {
        val EXPECTED_HEADERS = listOf(
            "Data",
            "Tipo de Movimentação",
            "Mercado",
            "Ticker",
            "Quantidade",
            "Preço Unitário",
            "Valor Bruto",
            "Instituição",
        )
        const val GROSS_SCALE = 2
    }
}
