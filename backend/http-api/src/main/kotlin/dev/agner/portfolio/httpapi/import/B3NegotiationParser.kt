package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.catalog.model.normalizedTicker
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import kotlinx.datetime.LocalDate
import org.apache.poi.ss.usermodel.Row
import org.apache.poi.ss.usermodel.WorkbookFactory
import org.springframework.stereotype.Component
import java.math.BigDecimal
@Component
class B3NegotiationParser : IImportParser {

    override fun shouldExecute(fileName: String, content: ByteArray): Boolean {
        if (!fileName.endsWith(".xlsx", ignoreCase = true)) {
            return false
        }
        val hasNeg = fileName.contains("neg", ignoreCase = true)
        val hasNegociacao = fileName.contains(
            "negociacao",
            ignoreCase = true,
        )
        return hasNeg || hasNegociacao
    }

    override fun parse(fileName: String, content: ByteArray): List<ImportRowPayload> {
        val workbook = WorkbookFactory.create(content.inputStream())
        val sheet = workbook.getSheetAt(0)
        val headerRow = sheet.getRow(0)
            ?: throw IllegalArgumentException(
                "B3 negotiation XLSX missing header row: $fileName",
            )

        val headers = (0 until headerRow.lastCellNum).mapNotNull { index ->
            val cell = headerRow.getCell(
                index,
                Row.MissingCellPolicy.RETURN_BLANK_AS_NULL,
            )
            cell?.stringCellValue?.trim()
        }

        val expectedHeaders = listOf(
            "Data",
            "Tipo de Movimentação",
            "Mercado",
            "Ticker",
            "Quantidade",
            "Preço Unitário",
            "Valor Bruto",
            "Instituição",
        )

        val matchedHeaders = expectedHeaders.filter { it in headers }
        if (matchedHeaders.size < expectedHeaders.size) {
            throw IllegalArgumentException(
                "B3 negotiation XLSX unrecognised layout in " +
                    "$fileName: found headers $headers",
            )
        }

        val result = mutableListOf<ImportRowPayload>()
        for (rowIndex in 1..sheet.lastRowNum) {
            val row = sheet.getRow(rowIndex) ?: continue
            if (row.firstCellNum < 0) {
                continue
            }

            val dateStr = getCellValue(row, "Data", headers)
            val sideStr = getCellValue(row, "Tipo de Movimentação", headers)
            val venueStr = getCellValue(row, "Mercado", headers)
            val tickerStr = getCellValue(row, "Ticker", headers)
            val qtyStr = getCellValue(row, "Quantidade", headers)
            val priceStr = getCellValue(row, "Preço Unitário", headers)
            val institutionStr = getCellValue(row, "Instituição", headers)

            val date = if (!dateStr.isNullOrEmpty()) {
                try {
                    LocalDate.parse(dateStr)
                } catch (e: Exception) {
                    throw IllegalArgumentException(
                        "Invalid date '$dateStr' in $fileName row $rowIndex",
                    )
                }
            } else {
                null
            }

            val ticker = tickerStr?.trim() ?: ""
            val normalizedTicker = ticker.normalizedTicker()

            if (date == null || ticker.isEmpty()) {
                val errorMsg = if (date == null && ticker.isEmpty()) {
                    "Missing date and ticker"
                } else if (date == null) {
                    "Missing date"
                } else {
                    "Missing ticker"
                }
                result.add(
                    ImportRowPayload(
                        date = date ?: LocalDate.parse("1970-01-01"),
                        side = TradeSide.BUY,
                        venue = venueStr ?: "",
                        ticker = normalizedTicker,
                        quantity = BigDecimal.ZERO,
                        unitPrice = BigDecimal.ZERO,
                        institutionName = institutionStr ?: "",
                        error = errorMsg,
                    ),
                )
                continue
            }

            val sideStrTrimmed = sideStr?.trim()?.uppercase() ?: ""
            val side = when {
                sideStrTrimmed == "C" || sideStrTrimmed == "COMPRA" -> TradeSide.BUY
                sideStrTrimmed == "V" || sideStrTrimmed == "VENDA" -> TradeSide.SELL
                else -> throw IllegalArgumentException(
                    "Unknown side '$sideStr' in $fileName row $rowIndex",
                )
            }

            val quantity = qtyStr?.toBigDecimalOrNull()
                ?: throw IllegalArgumentException(
                    "Invalid quantity '$qtyStr' in $fileName row $rowIndex",
                )
            val unitPrice = priceStr?.toBigDecimalOrNull()
                ?: throw IllegalArgumentException(
                    "Invalid unit price '$priceStr' in $fileName row $rowIndex",
                )

            if (quantity.signum() <= 0) {
                result.add(
                    ImportRowPayload(
                        date = date,
                        side = side,
                        venue = venueStr ?: "",
                        ticker = normalizedTicker,
                        quantity = quantity,
                        unitPrice = unitPrice,
                        institutionName = institutionStr ?: "",
                        error = "Quantity must be positive",
                    ),
                )
                continue
            }

            result.add(
                ImportRowPayload(
                    date = date,
                    side = side,
                    venue = venueStr ?: "",
                    ticker = normalizedTicker,
                    quantity = quantity,
                    unitPrice = unitPrice,
                    institutionName = institutionStr ?: "",
                ),
            )
        }

        workbook.close()
        return result
    }

    private fun getCellValue(
        row: Row,
        header: String,
        headers: List<String>,
    ): String? {
        val index = headers.indexOf(header)
        if (index < 0) {
            return null
        }
        val cell = row.getCell(
            index,
            Row.MissingCellPolicy.RETURN_BLANK_AS_NULL,
        )
        return cell?.toString()?.trim()
    }
}
