package dev.agner.portfolio.httpapi.configuration

import kotlinx.datetime.LocalDate
import kotlinx.datetime.toKotlinLocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DateUtil
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.ByteArrayInputStream
import java.math.BigDecimal

/**
 * Header-addressed reading of a B3 statement sheet, shared by the trades and the movements
 * parsers — the two read different sheets of the same export and were otherwise duplicating all
 * of this.
 *
 * Typed cells are read as themselves rather than round-tripped through their string form: POI
 * renders a numeric cell as `"100.0"`, and Brazilian-format parsing (strip `.`, swap `,` for `.`)
 * would turn that into 1000. Only genuine text cells go through the Brazilian number/date rules.
 */
class XlsxSheetReader private constructor(
    private val sheet: Sheet,
    private val columnIndexByHeader: Map<String, Int>,
    private val onError: (String) -> Nothing,
) {

    companion object {
        /**
         * Opens the first sheet and indexes its header row. [onError] turns a failure into the
         * caller's own parse exception, so this class stays independent of which domain uses it.
         */
        fun open(
            xlsxBytes: ByteArray,
            requiredColumns: List<String>,
            onError: (String) -> Nothing,
        ): XlsxSheetReader {
            val sheet = WorkbookFactory.create(ByteArrayInputStream(xlsxBytes)).getSheetAt(0)
            val headerRow = sheet.getRow(0) ?: onError("Empty spreadsheet")

            val columnIndexByHeader = headerRow.mapNotNull { it.stringValue()?.trim() }
                .withIndex()
                .associate { (index, header) -> header to index }

            val missing = requiredColumns.filterNot { it in columnIndexByHeader }
            if (missing.isNotEmpty()) onError("Missing expected columns: $missing")

            return XlsxSheetReader(sheet, columnIndexByHeader, onError)
        }

        private fun Cell.stringValue(): String? = when (cellType) {
            CellType.STRING -> stringCellValue
            CellType.BLANK -> null
            CellType.NUMERIC -> numericCellValue.toString()
            else -> toString()
        }
    }

    /** Every non-blank row below the header. */
    fun dataRows(): List<Row> = sheet.drop(1).filter { row -> row.any { it.stringValue()?.isNotBlank() == true } }

    fun Row.text(header: String): String? = cell(header)?.stringValue()?.trim()

    fun Row.requiredText(header: String): String =
        text(header) ?: onError("Missing $header on row ${rowNum + 1}")

    /** A real date cell, or `dd/MM/yyyy` text. */
    fun Row.requiredDate(header: String): LocalDate {
        val cell = cell(header) ?: onError("Missing $header on row ${rowNum + 1}")

        if (cell.cellType == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.localDateTimeCellValue.toLocalDate().toKotlinLocalDate()
        }

        val text = cell.stringValue()?.trim() ?: onError("Missing $header on row ${rowNum + 1}")
        return runCatching {
            val (day, month, year) = text.split("/").map { it.toInt() }
            LocalDate(year, month, day)
        }.getOrElse { onError("Unrecognized date '$text' on row ${rowNum + 1}") }
    }

    /** A real numeric cell, or Brazilian-format text (`1.234,56`). */
    fun Row.requiredDecimal(header: String): BigDecimal {
        val cell = cell(header) ?: onError("Missing $header on row ${rowNum + 1}")

        // BigDecimal.valueOf, not Double.toBigDecimal(): the latter is the exact-double constructor,
        // so a price of 26.09 comes back as 26.09000000000000341… A whole number keeps scale 0.
        if (cell.cellType == CellType.NUMERIC) {
            val number = cell.numericCellValue
            return if (number.isFinite() && number == Math.rint(number)) {
                BigDecimal.valueOf(number.toLong())
            } else {
                BigDecimal.valueOf(number)
            }
        }

        val text = cell.stringValue()?.trim() ?: onError("Missing $header on row ${rowNum + 1}")
        return runCatching {
            BigDecimal(text.replace(".", "").replace(",", "."))
        }.getOrElse { onError("Unrecognized number '$text' on row ${rowNum + 1}") }
    }

    private fun Row.cell(header: String): Cell? = columnIndexByHeader[header]?.let { getCell(it) }
}
