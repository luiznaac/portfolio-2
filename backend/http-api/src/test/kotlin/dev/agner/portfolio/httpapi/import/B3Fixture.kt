package dev.agner.portfolio.httpapi.import

import kotlinx.datetime.toJavaLocalDate
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Row
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

// The same data as exported from B3's Área do Investidor, whose header vocabulary differs from the
// broker layout above (Data do Negócio / Código de Negociação / Preço / Valor).
internal val B3_INVESTOR_HEADERS = listOf(
    "Data do Negócio",
    "Tipo de Movimentação",
    "Mercado",
    "Prazo/Vencimento",
    "Instituição",
    "Código de Negociação",
    "Quantidade",
    "Preço",
    "Valor",
)

// Builds an XLSX with real cell types — an Excel date cell for the date column and numeric cells
// for the numbers — so a test exercises POI's type-aware reading, not an all-strings workbook.
// `numericFormat`, when set, styles the numeric cells (e.g. a B3 export's `R$ #,##0.00`), so a
// test can exercise a formatter that `toString` would spoil.
internal fun b3Workbook(
    headers: List<String?>,
    rows: List<List<Any?>>,
    numericFormat: String? = null,
): ByteArray {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Sheet1")
    val dateStyle = workbook.createCellStyle().apply {
        dataFormat = workbook.creationHelper.createDataFormat().getFormat("yyyy-mm-dd")
    }
    val numericStyle = numericFormat?.let { format ->
        workbook.createCellStyle().apply {
            dataFormat = workbook.creationHelper.createDataFormat().getFormat(format)
        }
    }
    val headerRow = sheet.createRow(0)
    headers.forEachIndexed { index, header ->
        if (header != null) headerRow.createCell(index).setCellValue(header)
    }
    rows.forEachIndexed { rowIndex, values ->
        val row = sheet.createRow(rowIndex + 1)
        values.forEachIndexed { columnIndex, value ->
            writeCell(row, columnIndex, value, dateStyle, numericStyle)
        }
    }
    return ByteArrayOutputStream().use { out ->
        workbook.use { it.write(out) }
        out.toByteArray()
    }
}

private fun writeCell(row: Row, index: Int, value: Any?, dateStyle: CellStyle, numericStyle: CellStyle?) {
    when (value) {
        null -> Unit
        is String -> row.createCell(index).setCellValue(value)
        is Number -> row.createCell(index).apply {
            setCellValue(value.toDouble())
            numericStyle?.let { cellStyle = it }
        }
        is kotlinx.datetime.LocalDate -> row.createCell(index).apply {
            setCellValue(value.toJavaLocalDate())
            cellStyle = dateStyle
        }
        else -> error("Unsupported fixture cell value: $value")
    }
}
