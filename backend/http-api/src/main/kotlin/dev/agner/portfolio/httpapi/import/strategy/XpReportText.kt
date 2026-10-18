package dev.agner.portfolio.httpapi.import.strategy

import dev.agner.portfolio.usecase.strategy.InvalidStrategyReportException
import dev.agner.portfolio.usecase.strategy.UnsupportedStrategyReportException
import dev.agner.portfolio.usecase.strategy.model.EditionEntry
import dev.agner.portfolio.usecase.strategy.model.Rating
import dev.agner.portfolio.usecase.strategy.model.editionWeightProblem
import kotlinx.datetime.YearMonth
import org.apache.pdfbox.Loader
import org.apache.pdfbox.text.PDFTextStripper
import java.io.IOException
import java.math.BigDecimal

// Shared text handling for the XP report parsers. The reports are text PDFs; PDFBox's stripper
// (the same dependency PdfConverter uses) yields one page at a time, and every parser anchors on a
// header line inside that text rather than on a page number, since the summary and detail pages
// repeat the same table at different offsets.

// The two row fragments every XP layout shares — a ticker token and a pt-BR weight with its percent
// sign. Each layout composes them into its own row regex (the FII layout prints them reversed), so
// one change to the token shape moves all four parsers together.
internal const val TICKER_GROUP = "([A-Z][A-Z0-9]{3}\\d{1,2})"
internal const val WEIGHT_GROUP = "(\\d+(?:,\\d+)?)%"

internal fun pdfPages(fileName: String, content: ByteArray): List<String> =
    try {
        Loader.loadPDF(content).use { document ->
            val stripper = PDFTextStripper().apply { sortByPosition = true }
            (1..document.numberOfPages).map { page ->
                stripper.startPage = page
                stripper.endPage = page
                stripper.getText(document)
            }
        }
    } catch (exception: IOException) {
        throw UnsupportedStrategyReportException(fileName)
    }

// A PT-BR number as the reports print it: a comma decimal separator, dots as thousands separators
// and an optional trailing percent sign. "12,5%" -> 12.5, "1.234,56" -> 1234.56.
internal fun parsePtBrDecimal(raw: String): BigDecimal =
    BigDecimal(raw.trim().removeSuffix("%").replace(".", "").replace(",", "."))

internal fun parseRating(raw: String): Rating = when (raw.trim().uppercase()) {
    "COMPRA", "BUY" -> Rating.BUY
    "NEUTRO", "NEUTRAL" -> Rating.NEUTRAL
    "VENDA", "SELL" -> Rating.SELL
    else -> error("unknown rating '$raw'")
}

// Hard checks a report must pass before it may become an Edition (the shared rule lives in
// editionWeightProblem). The research is explicit that a silent bad parse is worse than a rejection
// (issue #93): the reviewer can import a corrected file.
internal fun requireValidWeights(fileName: String, entries: List<EditionEntry>) {
    editionWeightProblem(entries)?.let { throw InvalidStrategyReportException(fileName, it) }
}

// The cover page always names the publication month ("1 de setembro de 2026" / "Setembro 2026");
// the first month-with-year match wins, so a prose reference to an older month cannot shadow it.
internal fun parseReferenceMonth(text: String): YearMonth? =
    MONTH_PATTERN.find(text)?.let { match ->
        val month = MONTH_NUMBERS[match.groupValues[1].lowercase()] ?: return null
        YearMonth(match.groupValues[2].toInt(), month)
    }

internal fun requireReferenceMonth(fileName: String, pages: List<String>): YearMonth =
    parseReferenceMonth(pages.firstOrNull().orEmpty())
        ?: throw InvalidStrategyReportException(fileName, "the header names no reference month")

private val MONTH_NUMBERS = mapOf(
    "janeiro" to 1,
    "fevereiro" to 2,
    "março" to 3,
    "marco" to 3,
    "abril" to 4,
    "maio" to 5,
    "junho" to 6,
    "julho" to 7,
    "agosto" to 8,
    "setembro" to 9,
    "outubro" to 10,
    "novembro" to 11,
    "dezembro" to 12,
)

private val MONTH_PATTERN = Regex(
    "(?i)\\b(janeiro|fevereiro|março|marco|abril|maio|junho|julho|agosto|setembro|outubro|novembro|dezembro)" +
        "\\b(?:\\s+de)?\\s+(\\d{4})",
)
