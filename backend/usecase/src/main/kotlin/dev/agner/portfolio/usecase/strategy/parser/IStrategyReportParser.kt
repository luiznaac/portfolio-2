package dev.agner.portfolio.usecase.strategy.parser

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import kotlinx.datetime.LocalDate

// Port for extracting a broker's model-portfolio PDF into data. The concrete parser is line-based
// and recognizes both stock and FII rows; the PDF dependency stays in http-api.
interface IStrategyReportParser {
    fun parse(pdfBytes: ByteArray): ParsedStrategyReport
}

data class ParsedStrategyReport(
    val referenceDate: LocalDate,
    val changesText: String?,
    val targets: List<StrategyTarget>,
)

class StrategyReportParseException(detail: String) :
    DomainException(
        error = "strategy-report-invalid",
        userMessage = "The strategy report is invalid",
        detail = detail,
    )
