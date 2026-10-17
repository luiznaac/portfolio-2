package dev.agner.portfolio.httpapi.import

import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

private val POSITION_HEADERS = listOf(
    "Produto",
    "Instituição",
    "Código de Negociação",
    "Quantidade",
    "Preço de Fechamento",
    "Valor Atualizado",
)

class B3PositionParserTest : DescribeSpec({

    describe("B3PositionParser") {

        val parser = B3PositionParser()

        describe("shouldExecute") {
            it("claims a file whose sheets carry the position headers, regardless of filename") {
                val bytes = b3Workbook(POSITION_HEADERS, listOf(listOf("PETR4", "XP", "PETR4", 100, 38.5, 3850.0)))

                parser.shouldExecute("posicao-2026.xlsx", bytes) shouldBe true
            }

            it("rejects a negotiation workbook that lacks the position headers") {
                val bytes =
                    b3Workbook(
                        B3_HEADERS,
                        listOf(listOf("2026-10-14", "C", "VISTA", "PETR4", 100, 48.92, 4892.0, "XP")),
                    )

                parser.shouldExecute("neg.xlsx", bytes) shouldBe false
            }

            it("returns false without throwing for a non-xlsx file") {
                parser.shouldExecute("posicao.pdf", "not a workbook".toByteArray()) shouldBe false
            }

            it("returns false without throwing for a corrupt xlsx") {
                parser.shouldExecute("posicao.xlsx", byteArrayOf(0x50, 0x4B, 0x03, 0x04, 0x00)) shouldBe false
            }
        }

        describe("parse") {
            it("reads the ticker and quantity by header") {
                val bytes = b3Workbook(
                    POSITION_HEADERS,
                    listOf(
                        listOf("PETR4", "XP", "PETR4", 100, 38.5, 3850.0),
                        listOf("VALE3", "XP", "VALE3", 50, 60.0, 3000.0),
                    ),
                )

                val positions = parser.parse("posicao.xlsx", bytes)

                positions.size shouldBe 2
                positions[0].ticker shouldBe "PETR4"
                positions[0].quantity.compareTo(BigDecimal("100")) shouldBe 0
                positions[1].ticker shouldBe "VALE3"
                positions[1].quantity.compareTo(BigDecimal("50")) shouldBe 0
            }

            it("drops the trailing Total row") {
                val bytes = b3Workbook(
                    POSITION_HEADERS,
                    listOf(
                        listOf("PETR4", "XP", "PETR4", 100, 38.5, 3850.0),
                        listOf("Total", null, null, 100, null, 3850.0),
                    ),
                )

                parser.parse("posicao.xlsx", bytes).map { it.ticker } shouldBe listOf("PETR4")
            }

            it("normalizes the fractional-market suffix") {
                val bytes = b3Workbook(POSITION_HEADERS, listOf(listOf("ALUP11F", "XP", "ALUP11F", 100, 20.0, 2000.0)))

                parser.parse("posicao.xlsx", bytes).single().ticker shouldBe "ALUP11"
            }

            it("skips blank rows and rows without a quantity") {
                val bytes = b3Workbook(
                    POSITION_HEADERS,
                    listOf(
                        listOf(null, null, null, null, null, null),
                        listOf("PETR4", "XP", "PETR4", null, 38.5, 3850.0),
                        listOf("VALE3", "XP", "VALE3", 50, 60.0, 3000.0),
                    ),
                )

                parser.parse("posicao.xlsx", bytes).map { it.ticker } shouldBe listOf("VALE3")
            }

            it("locates the header row under a title row") {
                val bytes = b3Sheets(
                    TestSheet(
                        name = "Acoes",
                        title = "Posição de Ativos",
                        headers = POSITION_HEADERS,
                        rows = listOf(listOf("PETR4", "XP", "PETR4", 100, 38.5, 3850.0)),
                    ),
                )

                parser.parse("posicao.xlsx", bytes).single().ticker shouldBe "PETR4"
            }

            it("reads every asset-kind sheet that claims itself") {
                val bytes = b3Sheets(
                    TestSheet(
                        name = "Acoes",
                        headers = POSITION_HEADERS,
                        rows = listOf(listOf("PETR4", "XP", "PETR4", 100, 38.5, 3850.0)),
                    ),
                    TestSheet(
                        name = "Fundo de Investimento",
                        headers = POSITION_HEADERS,
                        rows = listOf(listOf("HGLG11", "XP", "HGLG11", 20, 160.0, 3200.0)),
                    ),
                    TestSheet(
                        name = "Renda Fixa",
                        headers = listOf("Produto", "Instituição", "Código", "Quantidade"),
                        rows = listOf(listOf("CDB", "XP", "CDB-XP", 1000)),
                    ),
                )

                parser.parse("posicao.xlsx", bytes).map { it.ticker } shouldBe listOf("PETR4", "HGLG11")
            }

            it("hard-fails loudly when no sheet carries the position headers") {
                val bytes = b3Workbook(listOf("Unknown", "Columns"), listOf(listOf("a", "b")))

                shouldThrow<IllegalArgumentException> { parser.parse("posicao.xlsx", bytes) }
            }
        }
    }
})
