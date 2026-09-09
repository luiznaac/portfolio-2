package dev.agner.portfolio.usecase.strategy.parser

import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import kotlinx.datetime.LocalDate

// Port for extracting a model-portfolio PDF (an XP report) into data. Format (stocks page 1 vs.
// FIIs page 2) is detected from the table header, not passed in — see the implementation in
// http-api, which is where the pdfbox dependency already lives (PdfConverter).
interface IStrategyReportParser {
    fun parse(pdfBytes: ByteArray): ParsedStrategyReport
}

data class ParsedStrategyReport(
    val referenceDate: LocalDate,
    val changesText: String?,
    val targets: List<StrategyTarget>,
)

class StrategyReportParseException(message: String) : Exception(message)
