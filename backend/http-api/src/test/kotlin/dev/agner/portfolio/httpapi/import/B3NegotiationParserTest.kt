package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldNotBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class B3NegotiationParserTest : DescribeSpec({

    describe("B3NegotiationParser") {

        val parser = B3NegotiationParser()
        val fixtureResource = javaClass.classLoader.getResource("b3-negociacao.xlsx")
            ?: throw IllegalStateException("Fixture b3-negociacao.xlsx not found in test resources")
        val fixtureBytes = fixtureResource.readBytes()

        describe("shouldExecute") {
            it("claims a file by its header row regardless of filename") {
                parser.shouldExecute("portfolio-export.xlsx", fixtureBytes) shouldBe true
            }

            it("rejects an xlsx whose header row is not the B3 layout") {
                val bytes = b3Workbook(listOf("Unknown", "Columns"), listOf(listOf("a", "b")))
                parser.shouldExecute("neg.xlsx", bytes) shouldBe false
            }

            it("returns false without throwing for a non-xlsx file") {
                parser.shouldExecute("neg.pdf", "not a workbook".toByteArray()) shouldBe false
            }

            it("returns false without throwing for a corrupt xlsx") {
                parser.shouldExecute("neg.xlsx", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)) shouldBe false
            }
        }

        describe("parse") {
            it("parses real numeric and date cells into quantity, unit price and date") {
                val rows = parser.parse("b3-negociacao.xlsx", fixtureBytes)

                rows.size shouldBe 2
                rows[0].ticker shouldBe "ALUP11"
                rows[0].side shouldBe TradeSide.BUY
                rows[0].date shouldBe LocalDate.parse("2026-10-14")
                rows[0].quantity.compareTo(BigDecimal("100")) shouldBe 0
                rows[0].unitPrice.compareTo(BigDecimal("48.92")) shouldBe 0
                rows[1].ticker shouldBe "PETR4"
                rows[1].side shouldBe TradeSide.SELL
                rows[1].quantity.compareTo(BigDecimal("50")) shouldBe 0
                rows[1].unitPrice.compareTo(BigDecimal("35.10")) shouldBe 0
            }

            it("reads columns by position so a blank header cannot shift them") {
                val headers = listOf(
                    "Data",
                    null,
                    "Tipo de Movimentação",
                    "Mercado",
                    "Ticker",
                    "Quantidade",
                    "Preço Unitário",
                    "Valor Bruto",
                    "Instituição",
                )
                val row = listOf(
                    LocalDate.parse("2026-10-14"),
                    null,
                    "C",
                    "VISTA",
                    "ALUP11F",
                    100,
                    48.92,
                    4892.00,
                    "XP INVESTIMENTOS",
                )

                val rows = parser.parse("shifted.xlsx", b3Workbook(headers, listOf(row)))

                rows.single().ticker shouldBe "ALUP11"
                rows.single().date shouldBe LocalDate.parse("2026-10-14")
                rows.single().quantity.compareTo(BigDecimal("100")) shouldBe 0
                rows.single().unitPrice.compareTo(BigDecimal("48.92")) shouldBe 0
            }

            it("reads columns by position so an extra column cannot shift them") {
                val headers = listOf(
                    "Data",
                    "Coluna Extra",
                    "Tipo de Movimentação",
                    "Mercado",
                    "Ticker",
                    "Quantidade",
                    "Preço Unitário",
                    "Valor Bruto",
                    "Instituição",
                )
                val row = listOf(
                    LocalDate.parse("2026-10-14"),
                    "ignored",
                    "C",
                    "VISTA",
                    "ALUP11F",
                    100,
                    48.92,
                    4892.00,
                    "XP INVESTIMENTOS",
                )

                val rows = parser.parse("extra-column.xlsx", b3Workbook(headers, listOf(row)))

                rows.single().ticker shouldBe "ALUP11"
                rows.single().quantity.compareTo(BigDecimal("100")) shouldBe 0
                rows.single().unitPrice.compareTo(BigDecimal("48.92")) shouldBe 0
            }

            it("strips the fractional-market F suffix from tickers") {
                val rows = parser.parse("b3-negociacao.xlsx", fixtureBytes)
                rows[0].ticker shouldBe "ALUP11"
            }

            it("flags a row whose gross does not match quantity times price") {
                val row = listOf(LocalDate.parse("2026-10-14"), "C", "VISTA", "PETR4", 100, 48.92, 9999.00, "XP")
                val rows = parser.parse("bad-gross.xlsx", b3Workbook(B3_HEADERS, listOf(row)))
                rows.single().error.shouldNotBeNull()
            }

            it("stages a row with a missing date as a null-date error row") {
                val row = listOf(null, "C", "VISTA", "PETR4", 100, 48.92, 4892.00, "XP")
                val rows = parser.parse("no-date.xlsx", b3Workbook(B3_HEADERS, listOf(row)))
                rows.single().date shouldBe null
                rows.single().error.shouldNotBeNull()
            }

            it("stages a row with an unparseable date as a null-date error row") {
                val row = listOf("not-a-date", "C", "VISTA", "PETR4", 100, 48.92, 4892.00, "XP")
                val rows = parser.parse("bad-date.xlsx", b3Workbook(B3_HEADERS, listOf(row)))
                rows.single().date shouldBe null
                rows.single().error.shouldNotBeNull()
            }

            it("stages a row with a valid date but blank ticker as an error row") {
                val row = listOf(LocalDate.parse("2026-10-14"), "C", "VISTA", null, 100, 48.92, 4892.00, "XP")
                val rows = parser.parse("no-ticker.xlsx", b3Workbook(B3_HEADERS, listOf(row)))
                rows.single().date shouldBe LocalDate.parse("2026-10-14")
                rows.single().error.shouldNotBeNull()
            }

            it("stages a row with an unknown side as a row-level error") {
                val row = listOf(LocalDate.parse("2026-10-14"), "X", "VISTA", "PETR4", 100, 48.92, 4892.00, "XP")
                val rows = parser.parse("bad-side.xlsx", b3Workbook(B3_HEADERS, listOf(row)))
                rows.single().error.shouldNotBeNull()
            }

            it("stages a row with a non-numeric quantity as a row-level error") {
                val row = listOf(LocalDate.parse("2026-10-14"), "C", "VISTA", "PETR4", "lots", 48.92, 4892.00, "XP")
                val rows = parser.parse("bad-quantity.xlsx", b3Workbook(B3_HEADERS, listOf(row)))
                rows.single().error.shouldNotBeNull()
            }

            it("hard-fails loudly on a claimed file whose header set is incomplete") {
                val bytes = b3Workbook(listOf("Data", "Ticker"), listOf(listOf(LocalDate.parse("2026-10-14"), "PETR4")))
                shouldThrow<IllegalArgumentException> { parser.parse("partial.xlsx", bytes) }
            }
        }
    }
})
