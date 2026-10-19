package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.income.model.IncomeCreation
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.util.Locale

// The columns the "Proventos a Receber" export carries, by their verbatim B3 headers. A column is
// located by its header cell's own position, so an extra column never shifts a later one.
private enum class IncomeColumn(val header: String) {
    PRODUCT("Produto"),
    EVENT_TYPE("Tipo de Evento"),
    PAYABLE_DATE("Previsão de pagamento"),
    QUANTITY("Quantidade"),
    UNIT_PRICE("Preço unitário"),
    NET("Valor líquido"),
}

// Parses the B3 "Proventos a Receber" export (eventos-*.xlsx) into income movements. Amounts are
// taken exactly as the file gives them: gross is quantity × unit price, net is the file's
// "Valor líquido" (already post-tax for listed-equity income and equal to gross for a FII
// rendimento), and retained is their difference — no withholding-rate logic. Income is per ticker,
// whole Position. It lives in http-api so the usecase layer never sees bytes or a file name.
@Component
class B3IncomeParser {

    // Recognition is by the header row alone, independent of filename: the layout *is* the column
    // set. A file we cannot open (non-xlsx, corrupt) is simply not ours — no throw.
    fun shouldExecute(fileName: String, content: ByteArray): Boolean = runCatching {
        WorkbookFactory.create(content.inputStream()).use { workbook ->
            isRecognised(columnHeaders(workbook.getSheetAt(0).getRow(0)))
        }
    }.getOrDefault(false)

    fun parse(fileName: String, content: ByteArray): List<IncomeCreation> =
        WorkbookFactory.create(content.inputStream()).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val headers = columnHeaders(sheet.getRow(0))
            require(isRecognised(headers)) {
                "B3 proventos XLSX unrecognised layout in $fileName: found headers ${headers.keys}"
            }
            parseRows(sheet, headers)
        }

    private fun isRecognised(headers: ColumnIndex) = IncomeColumn.entries.all { it.header in headers }

    private fun parseRows(sheet: Sheet, headers: ColumnIndex): List<IncomeCreation> {
        // POI's DataFormatter is not thread-safe, so it is per-parse rather than shared on this
        // singleton component; a concurrent upload must not reuse it.
        val formatter = DataFormatter(Locale.US)
        return (1..sheet.lastRowNum).mapNotNull { rowIndex ->
            sheet.getRow(rowIndex)
                ?.takeIf { it.firstCellNum >= 0 }
                ?.let { parseRow(it, headers, formatter) }
        }
    }

    // A row that is not a payable income event — a trailing "Total" row, a blank row, an event the
    // lifecycle does not model — contributes nothing. There is no review step behind this parser, so
    // a row it cannot interpret is dropped rather than staged.
    private fun parseRow(row: Row, headers: ColumnIndex, formatter: DataFormatter): IncomeCreation? {
        val ticker = readText(cell(row, headers, IncomeColumn.PRODUCT.header), formatter)
        val type = incomeTypeFrom(readText(cell(row, headers, IncomeColumn.EVENT_TYPE.header), formatter))
        val date = readDate(cell(row, headers, IncomeColumn.PAYABLE_DATE.header), formatter)
        val quantity = readNumber(cell(row, headers, IncomeColumn.QUANTITY.header), formatter)
        val unitPrice = readNumber(cell(row, headers, IncomeColumn.UNIT_PRICE.header), formatter)
        val net = readNumber(cell(row, headers, IncomeColumn.NET.header), formatter)

        if (ticker.isNullOrEmpty() || type == null || date == null) return null
        if (quantity == null || unitPrice == null || net == null) return null

        return IncomeCreation(
            ticker = ticker.normalizedTicker(),
            date = date,
            type = type,
            grossAmount = quantity.multiply(unitPrice),
            netAmount = net,
            provenance = Provenance.B3,
        )
    }

    private fun incomeTypeFrom(eventType: String?): IncomeType? =
        when (eventType?.trim()?.uppercase()) {
            "DIVIDENDO" -> IncomeType.DIVIDEND
            "JCP" -> IncomeType.JCP
            "RENDIMENTO" -> IncomeType.FUND_INCOME
            else -> null
        }
}
