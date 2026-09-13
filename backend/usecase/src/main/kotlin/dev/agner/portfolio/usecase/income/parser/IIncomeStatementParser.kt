package dev.agner.portfolio.usecase.income.parser

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.income.model.ReceivedIncome

/**
 * Port for the movements sheet of the same B3 statement export
 * [dev.agner.portfolio.usecase.brokeragenote.parser.IBrokerageNoteParser] reads for trades. Kept
 * as its own port because it is a different shape (no price or quantity, just a dated cash
 * movement) in a different domain. Corporate-action rows on that sheet are ignored here.
 */
interface IIncomeStatementParser {
    fun parse(xlsxBytes: ByteArray): List<ReceivedIncome>
}

class IncomeStatementParseException(detail: String) :
    DomainException(
        error = "income-statement-invalid",
        userMessage = "The income statement could not be parsed",
        detail = detail,
    )
