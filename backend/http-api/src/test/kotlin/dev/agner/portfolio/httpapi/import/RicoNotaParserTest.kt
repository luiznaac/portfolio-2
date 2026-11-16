package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import dev.agner.portfolio.usecase.plain
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import io.kotest.matchers.string.shouldContain

// The committed synthetic Rico nota is the only broker-note input: no real note or personal data
// enters git. It carries three pages — two notes with trades and one summary-only page — so the
// parser's page claim, note grouping and skip-empty-page rules are pinned against the same file the
// acceptance runbook uploads.
class RicoNotaParserTest : DescribeSpec({

    describe("the committed synthetic Rico nota") {

        val fixture = acceptanceDocsFixture("rico-nota-2019.pdf")
        val bytes = fixture.readBytes()
        val parser = RicoNotaParser()
        val rows = parser.parse(fixture.name, bytes)

        it("is claimed by its header, never by the filename") {
            parser.shouldExecute("nota-de-corretagem.pdf", bytes) shouldBe true
            parser.shouldExecute("rico-nota-2019.pdf", notARicoNote()) shouldBe false
        }

        it("stages one row per trade row of every note, skipping the page with no trades") {
            rows.map { row ->
                RicoFixtureRow(
                    note = row.noteNumber,
                    date = row.date.toString(),
                    side = row.side,
                    venue = row.venue,
                    rawDescription = row.rawDescription,
                    quantity = row.quantity.plain(),
                    unitPrice = row.unitPrice.plain(),
                    hasError = row.error != null,
                )
            } shouldBe FIXTURE_ROWS
            rows.map { it.institutionName }.toSet() shouldBe setOf("RICO INVESTIMENTOS")
        }

        it("leaves the ticker for the alias lookup and keeps the raw description") {
            rows.map { it.ticker } shouldBe List(FIXTURE_ROWS.size) { null }
            rows.map { it.rawDescription }.toSet() shouldBe FIXTURE_ROWS.mapNotNull { it.rawDescription }.toSet()
        }

        it("flags a quantity x price mismatch against the note's gross value") {
            val mismatch = rows.single { it.rawDescription == "ENGIE BRASIL ON NM" }

            mismatch.error shouldContain "does not match"
        }

        it("flags a LISTADOC/LISTADOV direction that disagrees with the D/C column") {
            val disagreement = rows.single { it.rawDescription == "FLEURY ON NM" }

            disagreement.side shouldBe TradeSide.SELL
            disagreement.error shouldContain "LISTADOC"
            disagreement.error shouldContain "C"
        }

        it("stages the fractional and the vista rows") {
            rows.filter { it.error == null }.map { it.venue }.toSet() shouldBe
                setOf("VISTA", "FRACIONARIO")
        }
    }
})

private data class RicoFixtureRow(
    val note: String?,
    val date: String,
    val side: TradeSide,
    val venue: String,
    val rawDescription: String?,
    val quantity: String,
    val unitPrice: String,
    val hasError: Boolean,
)

private val FIXTURE_ROWS = listOf(
    RicoFixtureRow(
        note = "123456",
        date = "2019-05-03",
        side = TradeSide.BUY,
        venue = "VISTA",
        rawDescription = "FII BCFF11 CI",
        quantity = "100",
        unitPrice = "100",
        hasError = false,
    ),
    RicoFixtureRow(
        note = "123456",
        date = "2019-05-03",
        side = TradeSide.BUY,
        venue = "FRACIONARIO",
        rawDescription = "FII KNRI11 ER",
        quantity = "7",
        unitPrice = "100",
        hasError = false,
    ),
    RicoFixtureRow(
        note = "123456",
        date = "2019-05-03",
        side = TradeSide.SELL,
        venue = "VISTA",
        rawDescription = "FII VISC11 NM",
        quantity = "50",
        unitPrice = "60",
        hasError = false,
    ),
    RicoFixtureRow(
        note = "123457",
        date = "2019-05-06",
        side = TradeSide.SELL,
        venue = "VISTA",
        rawDescription = "FII RBR PROP DM 88,27",
        quantity = "10",
        unitPrice = "88.27",
        hasError = false,
    ),
    RicoFixtureRow(
        note = "123457",
        date = "2019-05-06",
        side = TradeSide.BUY,
        venue = "VISTA",
        rawDescription = "ITAUUNIBANCO PN N1",
        quantity = "100",
        unitPrice = "11.5",
        hasError = false,
    ),
    RicoFixtureRow(
        note = "123457",
        date = "2019-05-06",
        side = TradeSide.BUY,
        venue = "VISTA",
        rawDescription = "ATIVO INEXISTENTE XPTO",
        quantity = "5",
        unitPrice = "10",
        hasError = false,
    ),
    RicoFixtureRow(
        note = "123457",
        date = "2019-05-06",
        side = TradeSide.BUY,
        venue = "VISTA",
        rawDescription = "ENGIE BRASIL ON NM",
        quantity = "10",
        unitPrice = "20",
        hasError = true,
    ),
    RicoFixtureRow(
        note = "123457",
        date = "2019-05-06",
        side = TradeSide.SELL,
        venue = "VISTA",
        rawDescription = "FLEURY ON NM",
        quantity = "10",
        unitPrice = "20",
        hasError = true,
    ),
)

// A real text PDF from the repo's docs tree that is not a Rico note: the claim is by content, so a
// PDF-shaped file with another broker's header must not be claimed.
private fun notARicoNote(): ByteArray =
    acceptanceDocsFixture("xp-relatorio-2026-10.pdf").readBytes()
