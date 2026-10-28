package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.income.model.IncomeType
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.nulls.shouldBeNull
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

private val MOVEMENTS_HEADERS = listOf(
    "Data",
    "Tipo de Movimentação",
    "Código de Negociação",
    "Instituição",
    "Quantidade",
    "Preço Unitário",
    "Valor",
    "Mercado",
    "Fator",
    "Novo Código de Negociação",
    "Número do Processo",
    "Data de Aprovação",
)

class B3MovementsParserTest : DescribeSpec({

    describe("B3MovementsParser") {
        val parser = B3MovementsParser()

        describe("shouldExecute") {
            it("claims a file whose header carries the movimentação columns") {
                parser.shouldExecute("movimentacao.xlsx", workbook(listOf(movementRow("Compra")))) shouldBe true
            }

            it("rejects a proventos workbook") {
                val bytes = b3Workbook(
                    listOf("Produto", "Tipo de Evento", "Previsão de pagamento", "Quantidade"),
                    listOf(listOf("PETR4", "DIVIDENDO", "2026-11-03", 10)),
                )

                parser.shouldExecute("eventos.xlsx", bytes) shouldBe false
            }

            it("returns false without throwing for a non-xlsx file") {
                parser.shouldExecute("movimentacao.pdf", "not a workbook".toByteArray()) shouldBe false
            }
        }

        describe("routing") {
            it("sends Compra and Venda to the trade ledger") {
                val routing = parser.parse(
                    "movimentacao.xlsx",
                    workbook(listOf(movementRow("Compra"), movementRow("Venda", ticker = "VALE3"))),
                )

                routing.trades.map { it.side } shouldBe listOf(TradeSide.BUY, TradeSide.SELL)
                routing.trades.map { it.ticker } shouldBe listOf("PETR4", "VALE3")
                routing.incomes shouldBe emptyList()
                routing.corporateActions shouldBe emptyList()
            }

            it("sends Dividendo, JCP and Rendimento to income reconciliation") {
                val routing = parser.parse(
                    "movimentacao.xlsx",
                    workbook(
                        listOf(
                            movementRow("Dividendo", gross = 78.39, quantity = 117, unitPrice = 0.67),
                            movementRow("Juros sobre Capital Próprio", gross = 65.06, quantity = 117, unitPrice = 0.67),
                            movementRow("Rendimento", ticker = "MXRF11", gross = 30.0, quantity = 20, unitPrice = 1.5),
                        ),
                    ),
                )

                routing.trades shouldBe emptyList()
                routing.incomes.map { it.type } shouldBe
                    listOf(IncomeType.DIVIDEND, IncomeType.JCP, IncomeType.FUND_INCOME)
            }

            it("keeps the gross when a quantity and unit price are present and the net from Valor") {
                val routing = parser.parse(
                    "movimentacao.xlsx",
                    workbook(listOf(movementRow("Dividendo", gross = 65.06, quantity = 117, unitPrice = 0.67))),
                )

                val income = routing.incomes.single()
                income.grossAmount.compareTo(BigDecimal("78.39")) shouldBe 0
                income.netAmount.compareTo(BigDecimal("65.06")) shouldBe 0
            }

            it("routes desdobramento and grupamento to corporate actions with their factor") {
                val routing = parser.parse(
                    "movimentacao.xlsx",
                    workbook(
                        listOf(
                            movementRow("Desdobramento", factor = 2),
                            movementRow("Grupamento", factor = 10),
                        ),
                    ),
                )

                routing.corporateActions.map { it.kind } shouldBe
                    listOf(CorporateActionKind.SPLIT, CorporateActionKind.REVERSE_SPLIT)
                val ratios = routing.corporateActions.map { requireNotNull(it.ratio) }
                ratios[0].compareTo(BigDecimal("2")) shouldBe 0
                ratios[1].compareTo(BigDecimal("10")) shouldBe 0
            }

            it("derives a cash event's per-share value from Valor and Quantidade") {
                val routing = parser.parse(
                    "movimentacao.xlsx",
                    workbook(listOf(movementRow("Restituição de Capital", gross = 150.0, quantity = 100))),
                )

                val action = routing.corporateActions.single()
                action.kind shouldBe CorporateActionKind.CAPITAL_RETURN
                requireNotNull(action.declaredValuePerShare).compareTo(BigDecimal("1.5")) shouldBe 0
            }

            it("routes a voluntary or merger event to the review kinds") {
                val routing = parser.parse(
                    "movimentacao.xlsx",
                    workbook(
                        listOf(
                            movementRow("Subscrição"),
                            movementRow("Cessão"),
                            movementRow("Incorporação"),
                            movementRow("Fusão"),
                            movementRow("Cisão"),
                        ),
                    ),
                )

                routing.corporateActions.map { it.kind } shouldBe listOf(
                    CorporateActionKind.RIGHTS_ISSUE,
                    CorporateActionKind.RIGHTS_ASSIGNMENT,
                    CorporateActionKind.INCORPORATION,
                    CorporateActionKind.MERGER,
                    CorporateActionKind.SPIN_OFF,
                )
            }

            it("queues an RF row and an unknown type as OTHER, never dropping either") {
                val routing = parser.parse(
                    "movimentacao.xlsx",
                    workbook(
                        listOf(
                            movementRow("Amortização Programada", gross = 100.0, quantity = 1),
                            movementRow("Pagamento de Juros", gross = 8.5, quantity = 1),
                            movementRow("Tipo que não existe"),
                        ),
                    ),
                )

                routing.corporateActions.map { it.kind } shouldBe List(3) { CorporateActionKind.OTHER }
                routing.corporateActions.map { it.rawType } shouldBe
                    listOf("Amortização Programada", "Pagamento de Juros", "Tipo que não existe")
            }

            it("queues a row with no movement type instead of dropping it") {
                val routing = parser.parse("movimentacao.xlsx", workbook(listOf(movementRow(""))))

                routing.corporateActions.single().kind shouldBe CorporateActionKind.OTHER
                routing.corporateActions.single().rawType shouldBe "(sem tipo)"
            }

            it("normalizes a fractional-market ticker") {
                val routing = parser.parse(
                    "movimentacao.xlsx",
                    workbook(listOf(movementRow("Compra", ticker = "ALUP11F"))),
                )

                routing.trades.single().ticker shouldBe "ALUP11"
            }

            it("assigns occurrence ordinals to same-day events of one kind") {
                val routing = parser.parse(
                    "movimentacao.xlsx",
                    workbook(
                        listOf(
                            movementRow("Desdobramento", factor = 2),
                            movementRow("Desdobramento", factor = 3),
                        ),
                    ),
                )

                routing.corporateActions.map { it.ordinal } shouldBe listOf(1, 2)
            }

            it("leaves a quantity event's factor null when the export carries no factor column") {
                val bytes = b3Workbook(
                    listOf("Data", "Tipo de Movimentação", "Código de Negociação", "Quantidade"),
                    listOf(listOf(LocalDate.parse("2026-10-20"), "Desdobramento", "PETR4", 200)),
                )

                parser.parse("movimentacao.xlsx", bytes).corporateActions.single().ratio.shouldBeNull()
            }
        }
    }
})

private fun workbook(rows: List<List<Any?>>) = b3Workbook(MOVEMENTS_HEADERS, rows)

private fun movementRow(
    type: String,
    ticker: String = "PETR4",
    gross: Number? = null,
    quantity: Number? = null,
    unitPrice: Number? = null,
    factor: Number? = null,
): List<Any?> = listOf(
    LocalDate.parse("2026-10-20"),
    type,
    ticker,
    "XP INVESTIMENTOS CCTVM S/A.",
    quantity,
    unitPrice,
    gross,
    "Mercado à Vista",
    factor,
    null,
    null,
    null,
)
