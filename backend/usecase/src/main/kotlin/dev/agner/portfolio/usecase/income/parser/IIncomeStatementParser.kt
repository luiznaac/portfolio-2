package dev.agner.portfolio.usecase.income.parser

import dev.agner.portfolio.usecase.commons.DomainException
import dev.agner.portfolio.usecase.income.model.ReceivedIncome

// Port for the "Movimentação" sheet of the same B3 statement export
// dev.agner.portfolio.usecase.brokeragenote.parser.IBrokerageNoteParser reads for trades — a
// different sheet of the same file, kept as its own port because it's a different shape (no
// price/quantity, just a dated cash movement) and a different domain (income, not trades).
// Corporate-action rows on this sheet (Desdobro, Bonificação em Ativos, ...) are ignored here,
// same simplification the brokerage-note import already made.
interface IIncomeStatementParser {
    fun parse(xlsxBytes: ByteArray): List<ReceivedIncome>
}

class IncomeStatementParseException(detail: String) :
    DomainException(
        error = "income-statement-invalid",
        userMessage = "The income statement could not be parsed",
        detail = detail,
    )
