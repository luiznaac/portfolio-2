package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.income.model.Provenance
import dev.agner.portfolio.usecase.income.model.TaxNature
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

private val PROVENTOS_HEADERS = listOf(
    "Produto",
    "Tipo",
    "Tipo de Evento",
    "Previsão de pagamento",
    "Instituição",
    "Conta",
    "Quantidade",
    "Preço unitário",
    "Valor líquido",
)

class B3IncomeParserTest : DescribeSpec({

    describe("B3IncomeParser") {
        val parser = B3IncomeParser()

        describe("shouldExecute") {
            it("claims a file whose header carries the proventos columns, regardless of filename") {
                val bytes = b3Workbook(
                    PROVENTOS_HEADERS,
                    listOf(proventosRow("PETR4", "DIVIDENDO", "2026-11-03", 117, 0.67, 65.06)),
                )

                parser.shouldExecute("eventos-2026.xlsx", bytes) shouldBe true
            }

            it("rejects a negotiation workbook that lacks the proventos columns") {
                val bytes = b3Workbook(
                    B3_HEADERS,
                    listOf(listOf("2026-10-14", "C", "VISTA", "PETR4", 100, 48.92, 4892.0, "XP")),
                )

                parser.shouldExecute("neg.xlsx", bytes) shouldBe false
            }

            it("rejects a position workbook") {
                val bytes = b3Workbook(
                    listOf("Produto", "Instituição", "Código de Negociação", "Quantidade"),
                    listOf(listOf("PETR4", "XP", "PETR4", 100)),
                )

                parser.shouldExecute("posicao.xlsx", bytes) shouldBe false
            }

            it("returns false without throwing for a non-xlsx file") {
                parser.shouldExecute("eventos.pdf", "not a workbook".toByteArray()) shouldBe false
            }

            it("returns false without throwing for a corrupt xlsx") {
                parser.shouldExecute("eventos.xlsx", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)) shouldBe false
            }
        }

        describe("parse") {
            it("reads the payable date, ticker and type by header") {
                val bytes = b3Workbook(
                    PROVENTOS_HEADERS,
                    listOf(
                        proventosRow("PETR4", "DIVIDENDO", "2026-11-03", 117, 0.67, 65.06),
                        proventosRow("HGCR11", "RENDIMENTO", "2026-11-10", 20, 1.50, 30.00),
                    ),
                )

                val events = parser.parse("eventos.xlsx", bytes)

                events.size shouldBe 2
                events[0].ticker shouldBe "PETR4"
                events[0].date shouldBe LocalDate.parse("2026-11-03")
                events[0].type shouldBe IncomeType.DIVIDEND
                events[1].ticker shouldBe "HGCR11"
                events[1].type shouldBe IncomeType.FUND_INCOME
            }

            it("maps the event vocabulary to the income types") {
                val bytes = b3Workbook(
                    PROVENTOS_HEADERS,
                    listOf(
                        proventosRow("PETR4", "JCP", "2026-11-03", 10, 1.0, 8.5),
                        proventosRow("VALE3", "DIVIDENDO", "2026-11-04", 10, 1.0, 9.0),
                        proventosRow("MXRF11", "RENDIMENTO", "2026-11-05", 10, 1.0, 10.0),
                    ),
                )

                parser.parse("eventos.xlsx", bytes).map { it.type } shouldBe
                    listOf(IncomeType.JCP, IncomeType.DIVIDEND, IncomeType.FUND_INCOME)
            }

            it("derives gross from quantity x unit price and retained from the difference with net") {
                // PETR4: 117 × 0.67 = 78.39 gross, 65.06 net → 13.33 retained.
                val bytes = b3Workbook(
                    PROVENTOS_HEADERS,
                    listOf(proventosRow("PETR4", "DIVIDENDO", "2026-11-03", 117, 0.67, 65.06)),
                )

                val event = parser.parse("eventos.xlsx", bytes).single()

                event.grossAmount.compareTo(BigDecimal("78.39")) shouldBe 0
                event.netAmount.compareTo(BigDecimal("65.06")) shouldBe 0
                event.retainedAmount.compareTo(BigDecimal("13.33")) shouldBe 0
            }

            it("stores the B3 provenance and the income tax nature") {
                val bytes = b3Workbook(
                    PROVENTOS_HEADERS,
                    listOf(proventosRow("PETR4", "DIVIDENDO", "2026-11-03", 117, 0.67, 65.06)),
                )

                val event = parser.parse("eventos.xlsx", bytes).single()

                event.provenance shouldBe Provenance.B3
                event.taxNature shouldBe TaxNature.INCOME
            }

            it("normalizes the fractional-market ticker suffix") {
                val bytes = b3Workbook(
                    PROVENTOS_HEADERS,
                    listOf(proventosRow("ALUP11F", "JCP", "2026-11-03", 10, 1.0, 8.5)),
                )

                parser.parse("eventos.xlsx", bytes).single().ticker shouldBe "ALUP11"
            }

            it("reads a dd/MM/yyyy payable date written as text") {
                val bytes = b3Workbook(
                    PROVENTOS_HEADERS,
                    listOf(listOf("PETR4", "ON", "DIVIDENDO", "03/11/2026", "XP", "1", 10, 1.0, 9.0)),
                )

                parser.parse("eventos.xlsx", bytes).single().date shouldBe LocalDate.parse("2026-11-03")
            }

            it("drops a trailing Total row and blank rows") {
                val bytes = b3Workbook(
                    PROVENTOS_HEADERS,
                    listOf(
                        proventosRow("PETR4", "DIVIDENDO", "2026-11-03", 117, 0.67, 65.06),
                        listOf(null, null, null, null, null, null, null, null, null),
                        listOf("Total", null, null, null, null, null, null, null, 65.06),
                    ),
                )

                parser.parse("eventos.xlsx", bytes).map { it.ticker } shouldBe listOf("PETR4")
            }

            it("drops a row whose event type is not an income event") {
                val bytes = b3Workbook(
                    PROVENTOS_HEADERS,
                    listOf(
                        proventosRow("PETR4", "DIVIDENDO", "2026-11-03", 117, 0.67, 65.06),
                        proventosRow("VALE3", "SUBSCRICAO", "2026-11-04", 10, 1.0, 9.0),
                    ),
                )

                parser.parse("eventos.xlsx", bytes).map { it.ticker } shouldBe listOf("PETR4")
            }

            it("hard-fails loudly when the header row is missing") {
                val bytes = b3Workbook(listOf("Unknown", "Columns"), listOf(listOf("a", "b")))

                shouldThrow<IllegalArgumentException> { parser.parse("eventos.xlsx", bytes) }
            }
        }
    }
})

private fun proventosRow(
    ticker: String,
    eventType: String,
    payableDate: String,
    quantity: Number,
    unitPrice: Number,
    net: Number,
): List<Any?> = listOf(
    ticker,
    "ON",
    eventType,
    LocalDate.parse(payableDate),
    "XP",
    "123",
    quantity,
    unitPrice,
    net,
)
