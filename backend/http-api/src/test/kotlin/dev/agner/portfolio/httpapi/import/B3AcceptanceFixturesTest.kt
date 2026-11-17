package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionKind
import dev.agner.portfolio.usecase.corporateaction.model.CorporateActionSource
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.plain
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

private const val MOVEMENTS = "b3-movimentacao.xlsx"
private const val MOVEMENTS_SPLIT = "b3-movimentacao-split-2020.xlsx"
private const val FAILED_NEGOTIATION = "b3-negociacao-failed.xlsx"

// The B3 acceptance artifacts, plus the 2018–2021 split export the history run imports. The combined
// image parses them with these same parsers, so a fixture that stops being recognised, or whose row
// drifts from the scenario the manifest drives, fails here instead of as a red browser intent.
class B3AcceptanceFixturesTest : DescribeSpec({

    describe("the movements acceptance fixture") {
        val parser = B3MovementsParser()
        val bytes = B3Fixtures.bytes(MOVEMENTS)

        it("is claimed by the B3 movimentações parser") {
            parser.shouldExecute(MOVEMENTS, bytes) shouldBe true
        }

        it("routes exactly one error-free VALE3 buy and nothing else") {
            val routing = parser.parse(MOVEMENTS, bytes)
            val trade = routing.trades.single()

            trade.side shouldBe TradeSide.BUY
            trade.ticker shouldBe "VALE3"
            trade.quantity.plain() shouldBe "10"
            trade.unitPrice.plain() shouldBe "60"
            trade.institutionName shouldBe "XP INVESTIMENTOS"
            trade.error shouldBe null
            routing.incomes shouldBe emptyList()
            routing.corporateActions shouldBe emptyList()
        }
    }

    // The B3 leg of the 2018–2021 history: a VALE3 split on the same ex-date a brapi payload
    // restates, so the end-to-end run proves B3 wins the shared natural key.
    describe("the 2018-2021 split acceptance fixture") {
        val parser = B3MovementsParser()
        val bytes = B3Fixtures.bytes(MOVEMENTS_SPLIT)

        it("is claimed by the B3 movimentações parser") {
            parser.shouldExecute(MOVEMENTS_SPLIT, bytes) shouldBe true
        }

        it("routes exactly one VALE3 split and no trade") {
            val routing = parser.parse(MOVEMENTS_SPLIT, bytes)
            val action = routing.corporateActions.single()

            routing.trades shouldBe emptyList()
            routing.incomes shouldBe emptyList()
            action.kind shouldBe CorporateActionKind.SPLIT
            action.ticker shouldBe "VALE3"
            action.exDate shouldBe LocalDate.parse("2020-02-10")
            action.ratio?.compareTo(BigDecimal("2")) shouldBe 0
            action.source shouldBe CorporateActionSource.B3_MOVEMENTS
        }
    }

    describe("the failed-state acceptance fixture") {
        val parser = B3NegotiationParser()
        val bytes = B3Fixtures.bytes(FAILED_NEGOTIATION)

        it("is claimed by the B3 negotiation parser") {
            parser.shouldExecute(FAILED_NEGOTIATION, bytes) shouldBe true
        }

        it("stages one error-free PETR4 buy") {
            val row = parser.parse(FAILED_NEGOTIATION, bytes).single()

            row.side shouldBe TradeSide.BUY
            row.ticker shouldBe "PETR4"
            row.quantity.plain() shouldBe "10"
            row.unitPrice.plain() shouldBe "30"
            row.error shouldBe null
        }
    }
})
