package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.plain
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import java.math.BigDecimal

// The curated acceptance artifact committed at docs/acceptance/fixtures/ is the file the runbook's
// end-to-end scenario uploads, so it is pinned against the same parser the running app uses. A
// fixture that stops being recognised, or whose rows drift from the enumerated scenario, fails here
// rather than surfacing as a red browser run on the combined image.
class B3AcceptanceFixtureTest : DescribeSpec({

    describe("the committed 2020 acceptance fixture") {

        val fixture = acceptanceDocsFixture("b3-negociacao-2020.xlsx")
        val parser = B3NegotiationParser()
        val bytes = fixture.readBytes()
        val rows = parser.parse(fixture.name, bytes)

        it("is claimed by the B3 negotiation parser") {
            parser.shouldExecute(fixture.name, bytes) shouldBe true
        }

        it("stages exactly the eleven enumerated rows with no row-level error") {
            rows.map { row ->
                FixtureRow(
                    date = row.date.toString(),
                    side = row.side,
                    ticker = row.ticker,
                    quantity = row.quantity.plain(),
                    unitPrice = row.unitPrice.plain(),
                )
            } shouldBe FIXTURE_ROWS
            rows.map { it.error } shouldBe List(FIXTURE_ROWS.size) { null }
            rows.map { it.venue }.toSet() shouldBe setOf("VISTA")
            rows.map { it.institutionName }.toSet() shouldBe setOf("XP INVESTIMENTOS")
        }

        it("nets the two long positions and the four mid-history negatives") {
            val quantities = rows.groupBy { it.ticker }.mapValues { (_, tickerRows) ->
                tickerRows.fold(BigDecimal.ZERO) { total, row ->
                    val signed = if (row.side == TradeSide.BUY) row.quantity else row.quantity.negate()
                    total + signed
                }.plain()
            }

            quantities shouldBe mapOf(
                "ALUP11" to "150",
                "PETR4" to "200",
                "HGLG11" to "-10",
                "KNCR11" to "-10",
                "VISC11" to "-10",
                "XPML11" to "-10",
            )
        }
    }
})

private data class FixtureRow(
    val date: String,
    val side: TradeSide,
    val ticker: String,
    val quantity: String,
    val unitPrice: String,
)

private val FIXTURE_ROWS = listOf(
    FixtureRow("2020-03-10", TradeSide.BUY, "ALUP11", "100", "20"),
    FixtureRow("2020-03-11", TradeSide.BUY, "PETR4", "200", "15"),
    FixtureRow("2020-03-12", TradeSide.SELL, "HGLG11", "30", "80"),
    FixtureRow("2020-04-02", TradeSide.SELL, "KNCR11", "20", "100"),
    FixtureRow("2020-05-06", TradeSide.SELL, "VISC11", "15", "90"),
    FixtureRow("2020-06-03", TradeSide.SELL, "XPML11", "12", "95"),
    FixtureRow("2020-08-12", TradeSide.BUY, "ALUP11", "50", "22"),
    FixtureRow("2020-09-15", TradeSide.BUY, "HGLG11", "20", "70"),
    FixtureRow("2020-10-01", TradeSide.BUY, "KNCR11", "10", "95"),
    FixtureRow("2020-12-01", TradeSide.BUY, "VISC11", "5", "85"),
    FixtureRow("2020-12-01", TradeSide.BUY, "XPML11", "2", "90"),
)
