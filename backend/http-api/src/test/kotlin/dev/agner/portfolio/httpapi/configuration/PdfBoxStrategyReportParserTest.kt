package dev.agner.portfolio.httpapi.configuration

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import dev.agner.portfolio.usecase.strategy.parser.StrategyReportParseException
import io.kotest.assertions.throwables.shouldThrow
import io.kotest.core.spec.style.DescribeSpec
import io.kotest.matchers.shouldBe
import kotlinx.datetime.LocalDate
import org.apache.pdfbox.pdmodel.PDDocument
import org.apache.pdfbox.pdmodel.PDPage
import org.apache.pdfbox.pdmodel.PDPageContentStream
import org.apache.pdfbox.pdmodel.font.PDType1Font
import org.apache.pdfbox.pdmodel.font.Standard14Fonts
import java.io.ByteArrayOutputStream
import java.math.BigDecimal

/**
 * There was no real broker report PDF available when this parser was written — these are
 * synthetic PDFs built from the plan's *description* of the layout, not a real sample. They
 * confirm the regex logic and the pdfbox extraction plumbing work; they cannot confirm the
 * actual column layout matches. Revisit against a real report at the first opportunity.
 */
class PdfBoxStrategyReportParserTest : DescribeSpec({

    val parser = PdfBoxStrategyReportParser()

    describe("parsing a stock model-portfolio report") {

        it("extracts ticker, weight and rating from a Companhia/Ticker/Peso/Rating/Preço-Alvo table") {
            val pdf = pdfOf(
                "Carteira Top - Setembro/2026",
                "Companhia         Ticker   Peso     Rating    Preço-Alvo",
                "Petrobras         PETR4    15,0%    COMPRA    R$ 45,00",
                "Vale              VALE3    10,0%    NEUTRO    R$ 70,50",
                "Itau Unibanco     ITUB4    75,0%    COMPRA    R$ 40,00",
                "",
                "Estamos adicionando ITUB4 e reduzindo o peso de VALE3 neste mes.",
            )

            val result = parser.parse(pdf)

            result.referenceDate shouldBe LocalDate(2026, 9, 1)
            result.changesText shouldBe "Estamos adicionando ITUB4 e reduzindo o peso de VALE3 neste mes."
            result.targets shouldBe listOf(
                StrategyTarget(
                    "PETR4",
                    BigDecimal("0.1500"),
                    "COMPRA",
                    BigDecimal("45.00"),
                ),
                StrategyTarget(
                    "VALE3",
                    BigDecimal("0.1000"),
                    "NEUTRO",
                    BigDecimal("70.50"),
                ),
                StrategyTarget(
                    "ITUB4",
                    BigDecimal("0.7500"),
                    "COMPRA",
                    BigDecimal("40.00"),
                ),
            )
        }

        it("parses the same bytes repeatedly, closing each document after extraction") {
            val pdf = pdfOf(
                "Carteira Top - Setembro/2026",
                "Companhia   Ticker   Peso     Rating    Preco-Alvo",
                "Petrobras   PETR4    100,0%   COMPRA    R$ 45,00",
                "Estamos adicionando PETR4.",
            )

            val first = parser.parse(pdf)
            val second = parser.parse(pdf)

            first shouldBe second
            first.referenceDate shouldBe LocalDate(2026, 9, 1)
            first.changesText shouldBe "Estamos adicionando PETR4."
            first.targets.map { it.ticker to it.weight } shouldBe listOf("PETR4" to BigDecimal("1.0000"))
        }

        it("extracts ticker and weight from a FII Peso/Segmento/Ticker/Recomendação/Nome table without rating") {
            val pdf = pdfOf(
                "Carteira Fundamentalista de FIIs - Setembro/2026",
                "Peso %    Segmento     Ticker     Recomendacao   Nome",
                "40,25%    Recebiveis   MCCI11     COMPRA         Mauá Capital",
                "59,75%    Tijolo       VILG11     COMPRA         Vinci Logistica",
            )

            val result = parser.parse(pdf)

            result.targets.map { it.ticker to it.weight } shouldBe listOf(
                "MCCI11" to BigDecimal("0.4025"),
                "VILG11" to BigDecimal("0.5975"),
            )
        }

        it("extracts a ticker whose root isn't pure letters, like B3's own B3SA3") {
            val pdf = pdfOf(
                "Carteira Top - Setembro/2026",
                "Companhia   Ticker   Peso     Rating   Preco-Alvo",
                "B3          B3SA3    20,0%    COMPRA   R$ 15,00",
                "Petrobras   PETR4    80,0%    COMPRA   R$ 45,00",
            )

            val result = parser.parse(pdf)

            result.targets.map { it.ticker } shouldBe listOf("B3SA3", "PETR4")
        }

        it("throws when the report has no competência to anchor the edition to") {
            val pdf = pdfOf(
                "Companhia   Ticker   Peso",
                "Petrobras   PETR4    100,0%",
            )

            shouldThrow<StrategyReportParseException> { parser.parse(pdf) }
        }

        it("stops changes text at the next table header") {
            val pdf = pdfOf(
                "Carteira Top - Setembro/2026",
                "Estamos adicionando PETR4.",
                "Desempenho",
                "PETR4 100,0%",
            )

            parser.parse(pdf).changesText shouldBe "Estamos adicionando PETR4."
        }
    }
})

private fun pdfOf(vararg lines: String): ByteArray {
    val document = PDDocument()
    val page = PDPage()
    document.addPage(page)

    PDPageContentStream(document, page).use { stream ->
        val font = PDType1Font(Standard14Fonts.FontName.HELVETICA)
        stream.setFont(font, 10f)
        stream.beginText()
        stream.newLineAtOffset(50f, 750f)
        lines.forEach { line ->
            stream.showText(line)
            stream.newLineAtOffset(0f, -14f)
        }
        stream.endText()
    }

    val output = ByteArrayOutputStream()
    document.save(output)
    document.close()
    return output.toByteArray()
}
