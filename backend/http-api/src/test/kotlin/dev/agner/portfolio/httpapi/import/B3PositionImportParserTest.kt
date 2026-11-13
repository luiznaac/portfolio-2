package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset

private val CUSTODY_HEADERS = listOf(
    "Produto",
    "Instituição",
    "Código de Negociação",
    "Quantidade",
    "Preço de Fechamento",
    "Valor Atualizado",
)

// The staging adapter for the B3 Posição export: the same layout the custody cross-check reads,
// mapped onto the import row shape. A custody line has no date, side or institution of its own, so
// the row carries the snapshot date and a custody venue; POSITIONS apply reads only ticker+quantity.
class B3PositionImportParserTest : DescribeSpec({

    describe("B3PositionImportParser") {

        val clock = Clock.fixed(Instant.parse("2026-11-13T10:00:00Z"), ZoneOffset.UTC)
        val parser = B3PositionImportParser(B3PositionParser(), clock)
        val bytes = b3Workbook(
            CUSTODY_HEADERS,
            listOf(
                listOf("PETR4", "XP", "PETR4", 100, 38.5, 3850.0),
                listOf("VALE3", "XP", "VALE3", 50, 60.0, 3000.0),
            ),
        )

        it("serves the POSITIONS destination") {
            parser.destination shouldBe ImportDestinationObject.POSITIONS
        }

        it("claims the same custody layout the position parser claims") {
            parser.shouldExecute("posicao.xlsx", bytes) shouldBe true
            parser.shouldExecute("posicao.pdf", "not a workbook".toByteArray()) shouldBe false
        }

        it("maps every custody line to a row carrying the ticker and the snapshot quantity") {
            val rows = parser.parse("posicao.xlsx", bytes)

            rows.map { it.ticker to it.quantity.compareTo(BigDecimal.ZERO) } shouldBe
                listOf("PETR4" to 1, "VALE3" to 1)
            rows.map { it.quantity } shouldBe listOf(BigDecimal("100"), BigDecimal("50"))
        }

        it("stamps the row with the upload date and the custody venue") {
            val row = parser.parse("posicao.xlsx", bytes).first()

            row.date shouldBe LocalDate.parse("2026-11-13")
            row.side shouldBe TradeSide.BUY
            row.venue shouldBe "CUSTODIA"
            row.unitPrice.compareTo(BigDecimal.ZERO) shouldBe 0
            row.institutionName shouldBe "CUSTODIA"
            row.error shouldBe null
        }
    }
})
