package dev.agner.portfolio.usecase.strategy.parser

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.strategy.model.StrategyTarget
import kotlinx.datetime.LocalDate

/**
 * A broker model-portfolio report already turned into data. The domain never sees the PDF: the
 * `http-api` adapter picks a parser for the file it was given and hands
 * [dev.agner.portfolio.usecase.strategy.StrategyEditionService] this shape.
 */
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
