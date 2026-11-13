package dev.agner.portfolio.httpapi.import

import dev.agner.portfolio.usecase.import.UnsupportedImportFileException
import dev.agner.portfolio.usecase.import.model.ImportDestinationObject
import dev.agner.portfolio.usecase.import.model.ImportRowPayload
import dev.agner.portfolio.usecase.listedasset.model.TradeSide
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import java.math.BigDecimal

class ImportParserResolverTest : StringSpec({

    val payload = ImportRowPayload(
        date = LocalDate.parse("2026-10-14"),
        side = TradeSide.BUY,
        venue = "VISTA",
        ticker = "PETR4",
        quantity = BigDecimal("100"),
        unitPrice = BigDecimal("48.92"),
        institutionName = "XP INVESTIMENTOS",
    )

    val custodyPayload = payload.copy(venue = "CUSTODIA", unitPrice = BigDecimal.ZERO)

    "resolve returns the rows of the first parser that claims the file" {
        val resolver = ImportParserResolver(
            listOf(StubParser(claims = true, rows = listOf(payload))),
        )

        resolver.resolve("any.xlsx", byteArrayOf(), ImportDestinationObject.TRADES) shouldBe
            listOf(payload)
    }

    "resolve fails loudly when no parser claims the file" {
        val resolver = ImportParserResolver(
            listOf(StubParser(claims = false, rows = emptyList())),
        )

        shouldThrow<UnsupportedImportFileException> {
            resolver.resolve("unknown.xlsx", byteArrayOf(), ImportDestinationObject.TRADES)
        }
    }

    "resolve only asks the parsers that serve the upload's destination" {
        val trades = StubParser(claims = true, rows = listOf(payload))
        val custody = StubParser(
            claims = true,
            rows = listOf(custodyPayload),
            destination = ImportDestinationObject.POSITIONS,
        )
        val resolver = ImportParserResolver(listOf(trades, custody))

        resolver.resolve("posicao.xlsx", byteArrayOf(), ImportDestinationObject.POSITIONS) shouldBe
            listOf(custodyPayload)
    }

    "resolve rejects a custody layout staged as trades" {
        val resolver = ImportParserResolver(
            listOf(
                StubParser(
                    claims = true,
                    rows = listOf(custodyPayload),
                    destination = ImportDestinationObject.POSITIONS,
                ),
            ),
        )

        shouldThrow<UnsupportedImportFileException> {
            resolver.resolve("posicao.xlsx", byteArrayOf(), ImportDestinationObject.TRADES)
        }
    }

    "resolve stages a real Área do Investidor negotiation export instead of rejecting it" {
        val resolver = ImportParserResolver(listOf(B3NegotiationParser()))
        val bytes = b3Workbook(
            B3_INVESTOR_HEADERS,
            listOf(listOf("11/08/2026", "Compra", "Mercado Fracionário", "-", "XP", "ALOS3F", 7, 26.09, 182.63)),
        )

        val rows = resolver.resolve("negociacao-2026-10-13-09-24-50.xlsx", bytes, ImportDestinationObject.TRADES)

        rows.single().ticker shouldBe "ALOS3"
        rows.single().date shouldBe LocalDate.parse("2026-08-11")
    }
})

private class StubParser(
    private val claims: Boolean,
    private val rows: List<ImportRowPayload>,
    override val destination: ImportDestinationObject = ImportDestinationObject.TRADES,
) : IImportParser {
    override fun shouldExecute(fileName: String, content: ByteArray) = claims

    override fun parse(fileName: String, content: ByteArray) = rows
}
