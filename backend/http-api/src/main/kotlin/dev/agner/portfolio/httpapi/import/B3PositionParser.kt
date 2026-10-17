package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.listedasset.model.CustodyPosition
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.util.Locale

// One claiming sheet: where its header row sits and which column each expected header owns.
private data class SheetLayout(val sheet: Sheet, val headerRowIndex: Int, val columns: ColumnIndex)

// Parses the B3 "Posição" (custody) export into the ticker/quantity pairs the ledger reconciliation
// compares against. The export has no average price — only quantity and a market value — so a
// position is cross-checked, never seeded, from it. B3 splits the report by asset kind
// across sheets, so a sheet claims itself by carrying both expected headers and every claiming
// sheet contributes.
@Component
class B3PositionParser {

    // Recognition is by content, not filename: a file we cannot open (non-xlsx, corrupt) is simply
    // not ours — no throw.
    fun shouldExecute(fileName: String, content: ByteArray): Boolean = runCatching {
        WorkbookFactory.create(content.inputStream()).use { workbook -> layouts(workbook).isNotEmpty() }
    }.getOrDefault(false)

    fun parse(fileName: String, content: ByteArray): List<CustodyPosition> =
        WorkbookFactory.create(content.inputStream()).use { workbook ->
            val layouts = layouts(workbook)
            require(layouts.isNotEmpty()) {
                "B3 position XLSX unrecognised layout in $fileName: " +
                    "no sheet carries '$TICKER_HEADER' and '$QUANTITY_HEADER'"
            }
            layouts.flatMap { parseSheet(it) }
        }

    private fun layouts(workbook: Workbook): List<SheetLayout> =
        (0..<workbook.numberOfSheets).mapNotNull { index -> layoutFor(workbook.getSheetAt(index)) }

    // The header row is located, not assumed at row 0: some B3 sheets open with a title row above
    // it. A sheet claims itself only when both expected headers appear in the same row.
    private fun layoutFor(sheet: Sheet): SheetLayout? {
        for (rowIndex in 0..minOf(sheet.lastRowNum, HEADER_SEARCH_LIMIT)) {
            val row = sheet.getRow(rowIndex) ?: continue
            val columns = headerColumns(row)
            if (TICKER_HEADER in columns && QUANTITY_HEADER in columns) {
                return SheetLayout(sheet, rowIndex, columns)
            }
        }
        return null
    }

    private fun headerColumns(row: Row): ColumnIndex =
        (0..<row.lastCellNum.toInt())
            .mapNotNull { index ->
                val name = row
                    .getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL)
                    ?.takeIf { it.cellType == CellType.STRING }
                    ?.stringCellValue
                    ?.trim()
                if (name.isNullOrEmpty()) null else name to index
            }
            .toMap()

    private fun parseSheet(layout: SheetLayout): List<CustodyPosition> {
        // POI's DataFormatter is not thread-safe, so it is per-parse rather than shared on this
        // singleton component.
        val formatter = DataFormatter(Locale.US)
        val sheet = layout.sheet
        return ((layout.headerRowIndex + 1)..sheet.lastRowNum).mapNotNull { rowIndex ->
            val row = sheet.getRow(rowIndex) ?: return@mapNotNull null
            val ticker = readText(cell(row, layout.columns, TICKER_HEADER), formatter)
            // The export ends sheets with a "Total" row (BDR, Renda Fixa, Tesouro Direto) whose
            // ticker cell carries the label rather than a paper — drop it, and any blank row.
            if (ticker.isNullOrEmpty() || ticker.equals(TOTAL_LABEL, ignoreCase = true)) {
                return@mapNotNull null
            }
            val quantity = readText(cell(row, layout.columns, QUANTITY_HEADER), formatter)?.toBigDecimalOrNull()
                ?: return@mapNotNull null
            CustodyPosition(ticker.normalizedTicker(), quantity)
        }
    }

    private companion object {
        const val TICKER_HEADER = "Código de Negociação"
        const val QUANTITY_HEADER = "Quantidade"
        const val TOTAL_LABEL = "Total"
        const val HEADER_SEARCH_LIMIT = 10
    }
}
