package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.brokeragenote.parser.BrokerageNoteParseException
import dev.agner.portfolio.usecase.trade.model.TradeSide
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.collections.shouldHaveSize
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.apache.poi.xssf.usermodel.XSSFWorkbook
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/**
 * Fixtures mirror a real B3 "Negociação de Ativos" export (September 2026): sheet named
 * "Negociação"; the full column set `Data do Negócio | Tipo de Movimentação | Mercado |
 * Prazo/Vencimento | Instituição | Código de Negociação | Quantidade | Preço | Valor`; the date as
 * text, quantity and price as numeric cells; one execution per row.
 */
class ApachePoiBrokerageNoteParserTest : DescribeSpec({

    val parser = ApachePoiBrokerageNoteParser()

    fun statement(vararg rows: List<Any>) = xlsxOf(
        listOf(
            "Data do Negócio", "Tipo de Movimentação", "Mercado", "Prazo/Vencimento", "Instituição",
            "Código de Negociação", "Quantidade", "Preço", "Valor",
        ),
        *rows,
    )

    describe("parsing a Negociação de Ativos export") {

        it("reads a buy and a sell, quantity always positive, price without float noise") {
            val result = parser.parse(
                statement(
                    TradeRow("11/08/2026", "Compra", "Mercado à Vista", "PETR4", 100, 35.50).toCells(),
                    TradeRow("11/08/2026", "Venda", "Mercado à Vista", "VALE3", 50, 70.25).toCells(),
                ),
            )

            result shouldBe listOf(
                dev.agner.portfolio.usecase.brokeragenote.parser.ParsedTrade(
                    LocalDate(2026, 8, 11), "PETR4", TradeSide.BUY, BigDecimal("100"), BigDecimal("35.5"),
                ),
                dev.agner.portfolio.usecase.brokeragenote.parser.ParsedTrade(
                    LocalDate(2026, 8, 11), "VALE3", TradeSide.SELL, BigDecimal("50"), BigDecimal("70.25"),
                ),
            )
        }

        it("normalises a fractional-market ticker to its canonical form") {
            // ALUP11 (round lot) and ALUP11F (fractional) are the same paper; B3 emits one row per
            // execution, so a single order lands as several trades on the canonical ticker.
            val result = parser.parse(
                statement(
                    TradeRow("10/08/2026", "Venda", "Mercado à Vista", "ALUP11", 100, 31.24).toCells(),
                    TradeRow("11/08/2026", "Venda", "Mercado Fracionário", "ALUP11F", 13, 31.56).toCells(),
                    TradeRow("11/08/2026", "Venda", "Mercado Fracionário", "B3SA3F", 6, 14.26).toCells(),
                    TradeRow("10/08/2026", "Compra", "Mercado à Vista", "ROXO34", 80, 11.81).toCells(),
                ),
            )

            result.map { it.ticker } shouldBe listOf("ALUP11", "ALUP11", "B3SA3", "ROXO34")
        }

        it("keeps prices exact — a numeric 26.09 cell must not become 26.0900000000…") {
            val result = parser.parse(
                statement(TradeRow("11/08/2026", "Compra", "Mercado Fracionário", "ALOS3F", 7, 26.09).toCells()),
            )

            result.single().price shouldBe BigDecimal("26.09")
        }

        it("keeps the real column index when a header cell is blank") {
            // POI's row iterator skips physically absent cells; the header index must come from
            // the cell's real column, or a spacer column shifts every later header.
            val xlsx = xlsxOf(
                listOf(
                    "Data do Negócio", null, "Tipo de Movimentação", "Mercado", "Prazo/Vencimento",
                    "Instituição", "Código de Negociação", "Quantidade", "Preço", "Valor",
                ),
                listOf("11/08/2026", null, "Compra", "Mercado à Vista", "-", "XP", "PETR4", 100, 35.50, 3550.0),
            )

            parser.parse(xlsx).single().ticker shouldBe "PETR4"
        }

        it("skips blank trailing rows") {
            val result = parser.parse(
                statement(
                    TradeRow("11/08/2026", "Compra", "Mercado à Vista", "PETR4", 100, 35.50).toCells(),
                    listOf("", "", "", "", "", "", "", "", ""),
                ),
            )

            result shouldHaveSize 1
        }

        it("fails loudly when a required column is missing") {
            val xlsx = xlsxOf(
                listOf("Data do Negócio", "Código de Negociação", "Quantidade", "Preço"),
                listOf<Any>("11/08/2026", "PETR4", 100, 35.50),
            )

            shouldThrow<BrokerageNoteParseException> { parser.parse(xlsx) }
        }

        it("fails loudly on an unrecognised trade side") {
            val note = statement(TradeRow("11/08/2026", "Aluguel", "Mercado à Vista", "PETR4", 100, 35.50).toCells())

            shouldThrow<BrokerageNoteParseException> { parser.parse(note) }
        }
    }
})

private data class TradeRow(
    val date: String,
    val side: String,
    val market: String,
    val ticker: String,
    val quantity: Number,
    val price: Number,
) {
    fun toCells(): List<Any> {
        val notional = quantity.toDouble() * price.toDouble()
        return listOf(date, side, market, "-", "XP INVESTIMENTOS CCTVM S/A.", ticker, quantity, price, notional)
    }
}

private fun xlsxOf(vararg rows: List<Any?>): ByteArray {
    val workbook = XSSFWorkbook()
    val sheet = workbook.createSheet("Negociação")

    rows.forEachIndexed { rowIndex, values ->
        val row = sheet.createRow(rowIndex)
        values.forEachIndexed { cellIndex, value ->
            // A null leaves the cell physically absent, which is what a real export with a spacer
            // column looks like to POI.
            if (value == null) return@forEachIndexed
            val cell = row.createCell(cellIndex)
            when (value) {
                is Number -> cell.setCellValue(value.toDouble())
                else -> cell.setCellValue(value.toString())
            }
        }
    }

    val out = ByteArrayOutputStream()
    workbook.write(out)
    return out.toByteArray()
}
