package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.income.model.ReceivedIncome
import dev.agner.portfolio.usecase.income.parser.IIncomeStatementParser
import dev.agner.portfolio.usecase.income.parser.IncomeStatementParseException
import dev.agner.portfolio.usecase.listedasset.model.DividendType
import kotlinx.datetime.LocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.CellType
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.io.ByteArrayInputStream
import java.math.BigDecimal
import java.util.Locale

/**
 * Reads the "Movimentação" sheet of the same B3 statement export
 * [ApachePoiBrokerageNoteParser] reads for trades. No sample export was available when this was
 * written — column names below (`Data`, `Movimentação`, `Produto`, `Valor da Operação`) are a
 * best-effort guess from B3's own documentation, not verified against a real file. Same caveat as
 * the trades parser: treat as a documented best guess to correct once a real export surfaces.
 * Rows whose "Movimentação" isn't a cash income type (Desdobro, Bonificação em Ativos, ...) are
 * silently skipped — corporate-action detection from this sheet is left for a later pass.
 */
@Component
class ApachePoiIncomeStatementParser : IIncomeStatementParser {

    override fun parse(xlsxBytes: ByteArray): List<ReceivedIncome> =
        WorkbookFactory.create(ByteArrayInputStream(xlsxBytes)).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            // DataFormatter is not thread-safe and this parser is a singleton, so build one per
            // parse call. It renders numeric cells with their own display format, which matters for
            // B3's numeric "Valor da Operação" cells — numericCellValue.toString() ignores the
            // format, and toBrDecimal would then strip the dot and read 199.5 as 1995.
            val formatter = DataFormatter(Locale.forLanguageTag("pt-BR"))
            val headerRow = sheet.getRow(0)
                ?: throw IncomeStatementParseException("Empty spreadsheet")
            val columnIndexByHeader = headerRow.mapNotNull { it.stringValue(formatter)?.trim() }
                .withIndex()
                .associate { (i, header) -> header to i }

            val missing = REQUIRED_COLUMNS.filterNot { it in columnIndexByHeader }
            if (missing.isNotEmpty()) {
                throw IncomeStatementParseException("Missing expected columns: $missing")
            }

            sheet.drop(1)
                .filter { row -> row.any { it.stringValue(formatter)?.isNotBlank() == true } }
                .mapNotNull { row -> parseRow(row, columnIndexByHeader, formatter) }
        }

    private fun parseRow(
        row: Row,
        columnIndexByHeader: Map<String, Int>,
        formatter: DataFormatter,
    ): ReceivedIncome? {
        fun cell(header: String): Cell? = columnIndexByHeader[header]?.let { row.getCell(it) }

        val movementText = cell(COLUMN_MOVEMENT)?.stringValue(formatter)?.trim() ?: ""
        val type = movementText.toDividendTypeOrNull() ?: return null

        val dateText = cell(COLUMN_DATE)?.stringValue(formatter)
            ?: throw IncomeStatementParseException("Missing $COLUMN_DATE on row ${row.rowNum + 1}")
        val ticker = cell(COLUMN_TICKER)?.stringValue(formatter)?.trim()
            ?: throw IncomeStatementParseException("Missing $COLUMN_TICKER on row ${row.rowNum + 1}")
        val amountText = cell(COLUMN_AMOUNT)?.stringValue(formatter)
            ?: throw IncomeStatementParseException("Missing $COLUMN_AMOUNT on row ${row.rowNum + 1}")

        return ReceivedIncome(
            date = parseDate(dateText, row.rowNum),
            ticker = ticker,
            type = type,
            amount = amountText.toBrDecimal(row.rowNum),
        )
    }

    private fun String.toDividendTypeOrNull(): DividendType? = when {
        contains("JRS", ignoreCase = true) || contains("JUROS", ignoreCase = true) -> DividendType.JCP
        contains("DIVIDENDO", ignoreCase = true) -> DividendType.DIVIDENDO
        contains("RENDIMENTO", ignoreCase = true) -> DividendType.RENDIMENTO
        else -> null
    }

    private fun parseDate(text: String, rowNum: Int): LocalDate =
        try {
            val (day, month, year) = text.trim().split("/").map { it.toInt() }
            LocalDate(year, month, day)
        } catch (_: Exception) {
            throw IncomeStatementParseException("Unrecognized date '$text' on row ${rowNum + 1}")
        }

    private fun String.toBrDecimal(rowNum: Int): BigDecimal =
        try {
            BigDecimal(trim().replace(".", "").replace(",", "."))
        } catch (_: NumberFormatException) {
            throw IncomeStatementParseException("Unrecognized number '$this' on row ${rowNum + 1}")
        }

    private fun Cell.stringValue(formatter: DataFormatter): String? = when (cellType) {
        CellType.STRING -> stringCellValue
        CellType.NUMERIC -> formatter.formatCellValue(this)
        CellType.BLANK -> null
        else -> formatter.formatCellValue(this)
    }

    private companion object {
        const val COLUMN_DATE = "Data"
        const val COLUMN_MOVEMENT = "Movimentação"
        const val COLUMN_TICKER = "Produto"
        const val COLUMN_AMOUNT = "Valor da Operação"
        val REQUIRED_COLUMNS = listOf(COLUMN_DATE, COLUMN_MOVEMENT, COLUMN_TICKER, COLUMN_AMOUNT)
    }
}
