package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.brokeragenote.parser.BrokerageNoteParseException
import dev.agner.portfolio.usecase.brokeragenote.parser.ParsedTrade
import dev.agner.portfolio.usecase.trade.model.TradeSide
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/**
 * No real B3 "Negociação de Ativos" export was available when this parser was written — these
 * spreadsheets are synthesized directly (via a *different* library, plain POI writer, than the
 * app's own reader) from B3's documented column layout, not a real sample. They confirm the
 * column-matching and number/date parsing logic; they cannot confirm the real export's layout
 * matches. Revisit against a real export at the first opportunity.
 */
class ApachePoiBrokerageNoteParserTest : DescribeSpec({

    val parser = ApachePoiBrokerageNoteParser()

    describe("parsing a Negociação de Ativos export") {

        it("extracts buy and sell trades with signed quantity resolved downstream") {
            val xlsx = xlsxOf(
                listOf("Data do Negócio", "Tipo de Movimentação", "Código de Negociação", "Quantidade", "Preço"),
                listOf("01/09/2026", "Compra", "PETR4", "100", "35,50"),
                listOf("02/09/2026", "Venda", "VALE3", "50", "70,25"),
            )

            val result = parser.parse(xlsx)

            result shouldBe listOf(
                ParsedTrade(
                    date = LocalDate(2026, 9, 1),
                    ticker = "PETR4",
                    side = TradeSide.BUY,
                    quantity = BigDecimal("100"),
                    price = BigDecimal("35.50"),
                ),
                ParsedTrade(
                    date = LocalDate(2026, 9, 2),
                    ticker = "VALE3",
                    side = TradeSide.SELL,
                    quantity = BigDecimal("50"),
                    price = BigDecimal("70.25"),
                ),
            )
        }

        it("skips blank trailing rows") {
            val xlsx = xlsxOf(
                listOf("Data do Negócio", "Tipo de Movimentação", "Código de Negociação", "Quantidade", "Preço"),
                listOf("01/09/2026", "Compra", "PETR4", "100", "35,50"),
                listOf("", "", "", "", ""),
            )

            parser.parse(xlsx).size shouldBe 1
        }

        it("fails loudly when a required column is missing") {
            val xlsx = xlsxOf(
                listOf("Data do Negócio", "Código de Negociação", "Quantidade", "Preço"),
                listOf("01/09/2026", "PETR4", "100", "35,50"),
            )

            shouldThrow<BrokerageNoteParseException> { parser.parse(xlsx) }
        }

        it("fails loudly on an unrecognized trade side") {
            val xlsx = xlsxOf(
                listOf("Data do Negócio", "Tipo de Movimentação", "Código de Negociação", "Quantidade", "Preço"),
                listOf("01/09/2026", "Aluguel", "PETR4", "100", "35,50"),
            )

            shouldThrow<BrokerageNoteParseException> { parser.parse(xlsx) }
        }
    }
})

private fun xlsxOf(vararg rows: List<String>): ByteArray {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Negociação de Ativos")

    rows.forEachIndexed { rowIndex, values ->
        val row = sheet.createRow(rowIndex)
        values.forEachIndexed { cellIndex, value -> row.createCell(cellIndex).setCellValue(value) }
    }

    val out = ByteArrayOutputStream()
    workbook.write(out)
    return out.toByteArray()
}
