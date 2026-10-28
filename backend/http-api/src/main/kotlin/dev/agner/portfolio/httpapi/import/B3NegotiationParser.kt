package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.LocalDate
import org.apache.poi.ss.usermodel.Cell
import org.apache.poi.ss.usermodel.DataFormatter
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.Sheet
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Locale

// The B3 export ships the same negotiation data under two header vocabularies — the broker
// "Negociação de Ativos" layout and the Área do Investidor "Negociação" layout. A column is
// identified by any of its names, so either export claims the file.
private enum class Column(val aliases: Set<String>) {
    DATE(setOf("Data", "Data do Negócio")),
    SIDE(setOf("Tipo de Movimentação")),
    VENUE(setOf("Mercado")),
    TICKER(setOf("Ticker", "Código de Negociação")),
    QUANTITY(setOf("Quantidade")),
    UNIT_PRICE(setOf("Preço Unitário", "Preço")),
    GROSS(setOf("Valor Bruto", "Valor")),
    INSTITUTION(setOf("Instituição")),
}

@Component
class B3NegotiationParser : IImportParser {

    // Recognition is by the header row alone, independent of filename: the layout *is* the column
    // set, and a name that merely contains "neg" is not evidence of anything. A file we cannot
    // open (non-xlsx, corrupt) is simply not ours — no throw.
    override fun shouldExecute(fileName: String, content: ByteArray): Boolean = runCatching {
        WorkbookFactory.create(content.inputStream()).use { workbook ->
            isRecognised(columnHeaders(workbook.getSheetAt(0).getRow(0)))
        }
    }.getOrDefault(false)

    override fun parse(fileName: String, content: ByteArray): List<ImportRowPayload> =
        WorkbookFactory.create(content.inputStream()).use { workbook ->
            val sheet = workbook.getSheetAt(0)
            val headers = columnHeaders(sheet.getRow(0))
            require(isRecognised(headers)) {
                "B3 negotiation XLSX unrecognised layout in $fileName: found headers ${headers.keys}"
            }
            parseRows(sheet, headers)
        }

    private fun isRecognised(headers: ColumnIndex) =
        Column.entries.all { column -> column.aliases.any { it in headers } }

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
        val date = readDate(cell(row, headers, Column.DATE), formatter)
        val sideText = readText(cell(row, headers, Column.SIDE), formatter)
        val venue = readText(cell(row, headers, Column.VENUE), formatter).orEmpty()
        val ticker = readText(cell(row, headers, Column.TICKER), formatter).orEmpty()
        val institutionName = readText(cell(row, headers, Column.INSTITUTION), formatter).orEmpty()
        val quantityCell = cell(row, headers, Column.QUANTITY)
        val unitPriceCell = cell(row, headers, Column.UNIT_PRICE)
        val grossCell = cell(row, headers, Column.GROSS)
        val side = sideFrom(sideText)
        val quantity = readNumber(quantityCell, formatter)
        val unitPrice = readNumber(unitPriceCell, formatter)
        val gross = readNumber(grossCell, formatter)

        // Every row-level problem stages the row with an `error` and a best-effort shape; none of
        // them blocks the file. A row is applicable only when `error` is null, so apply skips it.
        val error = when {
            date == null || ticker.isEmpty() -> missingDateOrTicker(date, ticker)
            side == null -> "Unknown side '$sideText'"
            quantity == null -> "Invalid quantity '${readText(quantityCell, formatter)}'"
            unitPrice == null -> "Invalid unit price '${readText(unitPriceCell, formatter)}'"
            quantity.signum() <= 0 -> "Quantity must be positive"
            else -> grossError(gross, quantity, unitPrice)
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
    private fun grossError(gross: BigDecimal?, quantity: BigDecimal, unitPrice: BigDecimal): String? {
        val gross = gross ?: return "Missing gross value"
        val expected = quantity.multiply(unitPrice).setScale(GROSS_SCALE, RoundingMode.HALF_UP)
        return if (gross.setScale(GROSS_SCALE, RoundingMode.HALF_UP).compareTo(expected) == 0) {
            null
        } else {
            "Gross $gross does not match quantity x price ($expected)"
        }
    }

    private fun cell(row: Row, headers: ColumnIndex, column: Column): Cell? =
        column.aliases.firstNotNullOfOrNull { headers[it] }
            ?.let { row.getCell(it, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL) }

    private companion object {
        const val GROSS_SCALE = 2
    }
}
