package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
private fun createXlsxBytes(headers: List<String>): ByteArray {
    val wb = XSSFWorkbook()
    val sheet = wb.createSheet("Sheet1")
    val row = sheet.createRow(0)
    headers.forEachIndexed { index, header ->
        row.createCell(index).setCellValue(header)
    }
    val out = ByteArrayOutputStream()
    wb.use { it.write(out) }
    return out.toByteArray()
}

class B3NegotiationParserTest : DescribeSpec({

    describe("B3NegotiationParser") {

        val parser = B3NegotiationParser()
        val fixtureResource = javaClass.classLoader.getResource("b3-negociacao.xlsx")
            ?: throw IllegalStateException("Fixture b3-negociacao.xlsx not found in test resources")
        val fixtureBytes = fixtureResource.readBytes()

        describe("shouldExecute") {
            it("claims B3 negotiation XLSX files") {
                parser.shouldExecute("b3-negociacao.xlsx", fixtureBytes) shouldBe true
            }
        }

        describe("parse") {
            it("parses B3 negotiation fixture into trade rows") {
                val rows = parser.parse("b3-negociacao.xlsx", fixtureBytes)
                rows.size shouldBe 2
                rows[0].ticker shouldBe "ALUP11"
                rows[0].side shouldBe TradeSide.BUY
                rows[1].ticker shouldBe "PETR4"
                rows[1].side shouldBe TradeSide.SELL
            }

            it("strips fractional-market F suffix from tickers") {
                val rows = parser.parse("b3-negociacao.xlsx", fixtureBytes)
                rows[0].ticker shouldBe "ALUP11"
            }

            it("hard-fails loudly on unrecognised layout") {
                val badBytes = createXlsxBytes(listOf("Unknown", "Columns"))
                shouldThrow<IllegalArgumentException> {
                    parser.parse("bad.xlsx", badBytes)
                }
            }
        }
    }
})
