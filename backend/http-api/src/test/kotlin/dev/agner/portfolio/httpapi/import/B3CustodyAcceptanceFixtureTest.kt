package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.plain
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe

// The custody acceptance artifact: the Browser intent on /history uploads this file into the
// Conferir custódia input, so it is pinned against the same parser the running app uses. A fixture
// that stops being recognised, or whose quantities drift from the drift table the runbook
// enumerates, fails here rather than as a red browser intent on the combined image.
class B3CustodyAcceptanceFixtureTest : DescribeSpec({

    describe("the committed custody acceptance fixture") {

        val fixture = acceptanceDocsFixture("posicao-custodia-2020.xlsx")
        val parser = B3PositionParser()
        val bytes = fixture.readBytes()
        val rows = parser.parse(fixture.name, bytes)

        it("is claimed by the B3 position parser") {
            parser.shouldExecute(fixture.name, bytes) shouldBe true
        }

        it("reads the six custody quantities the drift table asserts") {
            rows.map { it.ticker to it.quantity.plain() } shouldBe listOf(
                "ALUP11" to "150",
                "PETR4" to "200",
                "HGLG11" to "60",
                "KNCR11" to "40",
                "VISC11" to "25",
                "XPML11" to "20",
            )
        }
    }
})
