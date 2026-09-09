package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.income.model.ReceivedIncome
import dev.agner.portfolio.usecase.income.parser.IncomeStatementParseException
import dev.agner.portfolio.usecase.listedasset.model.DividendType.DIVIDENDO
import dev.agner.portfolio.usecase.listedasset.model.DividendType.JCP
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/**
 * No real B3 "Movimentação" export was available when this parser was written — same caveat as
 * [ApachePoiBrokerageNoteParserTest]: these spreadsheets are synthesized from the documented
 * column layout, not a real sample.
 */
class ApachePoiIncomeStatementParserTest : DescribeSpec({

    val parser = ApachePoiIncomeStatementParser()

    describe("parsing a Movimentação export") {

        it("extracts dividend and JCP rows, skipping corporate-action rows") {
            val xlsx = xlsxOf(
                listOf("Data", "Movimentação", "Produto", "Valor da Operação"),
                listOf("15/06/2026", "Dividendo", "PETR4", "199,50"),
                listOf("15/06/2026", "Juros Sobre Capital Próprio", "ITUB4", "85,00"),
                listOf("10/06/2026", "Desdobro", "VALE3", "0,00"),
            )

            val result = parser.parse(xlsx)

            result shouldBe listOf(
                ReceivedIncome(LocalDate(2026, 6, 15), "PETR4", DIVIDENDO, BigDecimal("199.50")),
                ReceivedIncome(LocalDate(2026, 6, 15), "ITUB4", JCP, BigDecimal("85.00")),
            )
        }

        it("fails loudly when a required column is missing") {
            val xlsx = xlsxOf(
                listOf("Data", "Produto", "Valor da Operação"),
                listOf("15/06/2026", "PETR4", "199,50"),
            )

            shouldThrow<IncomeStatementParseException> { parser.parse(xlsx) }
        }
    }
})

private fun xlsxOf(vararg rows: List<String>): ByteArray {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Movimentação")

    rows.forEachIndexed { rowIndex, values ->
        val row = sheet.createRow(rowIndex)
        values.forEachIndexed { cellIndex, value -> row.createCell(cellIndex).setCellValue(value) }
    }

    val out = ByteArrayOutputStream()
    workbook.write(out)
    return out.toByteArray()
}
