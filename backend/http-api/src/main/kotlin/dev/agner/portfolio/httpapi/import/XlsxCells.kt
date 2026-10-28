package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.commons.brazilianLocalDateFormat
import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import java.math.BigDecimal

// A header name mapped to the column index it occupies in the sheet. Shared by the broker-file
// parsers — all of them locate their columns the same way, by the header cell's own position.
internal typealias ColumnIndex = Map<String, Int>

// Shared POI cell plumbing for the broker-file parsers: locate a named cell within an
// already-located header row, then read it through a per-parse DataFormatter. Every parser reads
// its sheets this way, so the helpers live here rather than being copied into each.
internal fun readText(cell: Cell?, formatter: DataFormatter): String? =
    cell?.let { formatter.formatCellValue(it).trim() }?.takeIf { it.isNotEmpty() }

internal fun cell(row: Row, headers: ColumnIndex, name: String): Cell? =
    headers[name]?.let { row.getCell(it, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) }

// Locates each header by the cell's own position, never by an index into a compacted header list: a
// blank or extra header before a real column would otherwise shift every later column and read the
// wrong cell. A non-string header cell is skipped rather than throwing.
internal fun columnHeaders(row: Row?): ColumnIndex {
    if (row == null) return emptyMap()
    return (0..<row.lastCellNum.toInt())
        .mapNotNull { index ->
            val headerCell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)
                ?: return@mapNotNull null
            if (headerCell.cellType != CellType.STRING) return@mapNotNull null
            headerCell.stringCellValue.trim().takeIf { it.isNotEmpty() }?.let { it to index }
        }
        .toMap()
}

// A numeric cell is read as its number, not through `DataFormatter`: a B3 export styles the money
// columns (e.g. `R$   26.09`), and the formatted string is not a decimal any parser can read. A
// text cell still goes through the formatter for its own value.
internal fun readNumber(cell: Cell?, formatter: DataFormatter): BigDecimal? = when {
    cell == null -> null
    cell.cellType == CellType.NUMERIC -> BigDecimal.valueOf(cell.numericCellValue)
    else -> readText(cell, formatter)?.toBigDecimalOrNull()
}

// A real B3 export stores the date as an Excel date cell; one re-saved as text writes `dd/MM/yyyy`
// (or ISO). Read type-aware: the raw `toString()` of a date cell is an Excel serial, and the two
// text layouts are not interchangeable.
internal fun readDate(cell: Cell?, formatter: DataFormatter): LocalDate? {
    if (cell == null) return null
    if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
        return cell.localDateTimeCellValue.toLocalDate().toKotlinLocalDate()
    }
    val text = readText(cell, formatter) ?: return null
    return runCatching { LocalDate.parse(text) }.getOrNull()
        ?: runCatching { LocalDate.parse(text, brazilianLocalDateFormat) }.getOrNull()
}
