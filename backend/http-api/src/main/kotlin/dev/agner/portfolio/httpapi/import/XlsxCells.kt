package dev.agner.portfolio.httpapi.import

import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row

// Shared POI cell plumbing for the broker-file parsers: locate a named cell within an
// already-located header row, then read it through a per-parse DataFormatter. Both the negotiation
// and position parsers read their sheets this way, so the helpers live here rather than being
// copied into each.
internal fun readText(cell: Cell?, formatter: DataFormatter): String? =
    cell?.let { formatter.formatCellValue(it).trim() }?.takeIf { it.isNotEmpty() }

internal fun cell(row: Row, headers: ColumnIndex, name: String): Cell? =
    headers[name]?.let { row.getCell(it, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) }
