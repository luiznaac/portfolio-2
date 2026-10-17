package dev.agner.portfolio.httpapi.import

import kotlinx.datetime.toJavaLocalDate
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Workbook
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream

// The B3 "Negociação de Ativos" header set, as its own source of truth so a parser test asserts
// against the layout rather than against the parser's own copy of it.
internal val B3_HEADERS = listOf(
    "Data",
    "Tipo de Movimentação",
    "Mercado",
    "Ticker",
    "Quantidade",
    "Preço Unitário",
    "Valor Bruto",
    "Instituição",
)

// One sheet of a fabricated workbook: an optional title row, a header row, then data rows. B3's
// exports put a title above the Negociação headers on some sheets, so the builder can too.
internal data class TestSheet(
    val name: String,
    val headers: List<String?>,
    val rows: List<List<Any?>>,
    val title: String? = null,
)

// Builds a single-sheet XLSX with real cell types — an Excel date cell for the date column and
// numeric cells for the numbers — so a test exercises POI's type-aware reading, not an all-strings
// workbook.
internal fun b3Workbook(headers: List<String?>, rows: List<List<Any?>>): ByteArray =
    b3Sheets(TestSheet(name = "Sheet1", headers = headers, rows = rows))

// Builds a multi-sheet XLSX so a parser that reads several asset-kind sheets can be exercised.
internal fun b3Sheets(vararg sheets: TestSheet): ByteArray {
    val workbook = XSSFWorkbook()
    val dateStyle = workbook.createCellStyle().apply {
        dataFormat = workbook.creationHelper.createDataFormat().getFormat("yyyy-mm-dd")
    }
    sheets.forEach { sheet -> writeSheet(workbook, sheet, dateStyle) }
    return ByteArrayOutputStream().use { out ->
        workbook.use { it.write(out) }
        out.toByteArray()
    }
}

private fun writeSheet(workbook: Workbook, sheet: TestSheet, dateStyle: CellStyle) {
    val ws = workbook.createSheet(sheet.name)
    var rowIndex = 0
    if (sheet.title != null) {
        ws.createRow(rowIndex++).createCell(0).setCellValue(sheet.title)
    }
    val headerRow = ws.createRow(rowIndex++)
    sheet.headers.forEachIndexed { index, header ->
        if (header != null) headerRow.createCell(index).setCellValue(header)
    }
    sheet.rows.forEach { values ->
        val row = ws.createRow(rowIndex++)
        values.forEachIndexed { columnIndex, value -> writeCell(row, columnIndex, value, dateStyle) }
    }
}

private fun writeCell(row: Row, index: Int, value: Any?, dateStyle: CellStyle) {
    when (value) {
        null -> Unit
        is String -> row.createCell(index).setCellValue(value)
        is Number -> row.createCell(index).setCellValue(value.toDouble())
        is kotlinx.datetime.LocalDate -> row.createCell(index).apply {
            setCellValue(value.toJavaLocalDate())
            cellStyle = dateStyle
        }
        else -> error("Unsupported fixture cell value: $value")
    }
}
