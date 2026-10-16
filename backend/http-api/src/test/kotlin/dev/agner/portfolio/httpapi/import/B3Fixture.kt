package dev.agner.portfolio.httpapi.import

import kotlinx.datetime.toJavaLocalDate
import org.apache.poi.ss.usermodel.CellStyle
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.time.LocalDate

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

// A B3 "Negociação de Ativos" workbook with real cell types: the date column is an Excel date
// cell and the numeric columns are numeric cells, as a real export produces — not strings.
internal fun b3NegotiationFixtureBytes(): ByteArray = b3Workbook(
    headers = B3_HEADERS,
    rows = listOf(
        listOf(LocalDate.of(2026, 10, 14), "C", "VISTA", "ALUP11F", 100, 48.92, 4892.00, "XP INVESTIMENTOS"),
        listOf(LocalDate.of(2026, 10, 14), "V", "FRAC", "PETR4", 50, 35.10, 1755.00, "BTG PACTUAL"),
    ),
)

internal fun b3Workbook(headers: List<String?>, rows: List<List<Any?>>): ByteArray {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Sheet1")
    val dateStyle = workbook.createCellStyle().apply {
        dataFormat = workbook.creationHelper.createDataFormat().getFormat("yyyy-mm-dd")
    }
    val headerRow = sheet.createRow(0)
    headers.forEachIndexed { index, header ->
        if (header != null) headerRow.createCell(index).setCellValue(header)
    }
    rows.forEachIndexed { rowIndex, values ->
        val row = sheet.createRow(rowIndex + 1)
        values.forEachIndexed { columnIndex, value -> writeCell(row, columnIndex, value, dateStyle) }
    }
    return ByteArrayOutputStream().use { out ->
        workbook.use { it.write(out) }
        out.toByteArray()
    }
}

private fun writeCell(row: Row, index: Int, value: Any?, dateStyle: CellStyle) {
    when (value) {
        null -> Unit
        is String -> row.createCell(index).setCellValue(value)
        is Number -> row.createCell(index).setCellValue(value.toDouble())
        is LocalDate -> writeDate(row, index, value, dateStyle)
        is kotlinx.datetime.LocalDate -> writeDate(row, index, value.toJavaLocalDate(), dateStyle)
        else -> error("Unsupported fixture cell value: $value")
    }
}

private fun writeDate(row: Row, index: Int, value: LocalDate, dateStyle: CellStyle) {
    row.createCell(index).apply {
        setCellValue(value)
        cellStyle = dateStyle
    }
}
